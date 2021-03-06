/*
 * Copyright (C) 2015-2019, metaphacts GmbH
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, you can receive a copy
 * of the GNU Lesser General Public License from http://www.gnu.org/
 */

package com.metaphacts.federation.repository.evaluation;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration;
import org.eclipse.rdf4j.common.lang.ObjectUtil;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.AbstractAggregateOperator;
import org.eclipse.rdf4j.query.algebra.AggregateOperator;
import org.eclipse.rdf4j.query.algebra.Avg;
import org.eclipse.rdf4j.query.algebra.Count;
import org.eclipse.rdf4j.query.algebra.Group;
import org.eclipse.rdf4j.query.algebra.GroupConcat;
import org.eclipse.rdf4j.query.algebra.GroupElem;
import org.eclipse.rdf4j.query.algebra.MathExpr.MathOp;
import org.eclipse.rdf4j.query.algebra.Max;
import org.eclipse.rdf4j.query.algebra.Min;
import org.eclipse.rdf4j.query.algebra.Sample;
import org.eclipse.rdf4j.query.algebra.Sum;
import org.eclipse.rdf4j.query.algebra.ValueExpr;
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategy;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet;
import org.eclipse.rdf4j.query.algebra.evaluation.ValueExprEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.util.MathUtil;
import org.eclipse.rdf4j.query.algebra.evaluation.util.ValueComparator;
import org.eclipse.rdf4j.query.impl.EmptyBindingSet;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.google.common.collect.Maps;
import com.metaphacts.federation.sparql.algebra.ServiceCallAggregate;
import com.google.common.collect.Lists;

/**
 * A modification of the standard RDF4J {@link GroupIterator}
 * which is able to process custom aggregates.
 * 
 * @author Andriy Nikolov an@metaphacts.com
 *
 */
public class MpGroupIterator extends CloseableIteratorIteration<BindingSet, QueryEvaluationException> {

    /*-----------*
     * Constants *
     *-----------*/

    private final SimpleValueFactory vf = SimpleValueFactory.getInstance();

    private final MpFederationStrategy strategy;

    private final BindingSet parentBindings;

    private final Group group;

    private volatile boolean initialized = false;

    private final Object lock = new Object();

    private final File tempFile;

    private final DB db;

    /**
     * Number of items cached before internal collections are synced to disk. If set to 0, no disk-syncing is
     * done and all internal caching is kept in memory.
     */
    private final long iterationCacheSyncThreshold;

    /*--------------*
     * Constructors *
     *--------------*/

    public MpGroupIterator(MpFederationStrategy strategy, Group group, BindingSet parentBindings)
        throws QueryEvaluationException
    {
        this(strategy, group, parentBindings, 0);
    }

    public MpGroupIterator(MpFederationStrategy strategy, Group group, BindingSet parentBindings,
            long iterationCacheSyncThreshold)
        throws QueryEvaluationException
    {
        this.strategy = strategy;
        this.group = group;
        this.parentBindings = parentBindings;
        this.iterationCacheSyncThreshold = iterationCacheSyncThreshold;

        if (this.iterationCacheSyncThreshold > 0) {
            try {
                this.tempFile = File.createTempFile("group-eval", null);
            }
            catch (IOException e) {
                throw new QueryEvaluationException("could not initialize temp db", e);
            }
            this.db = DBMaker.newFileDB(tempFile).deleteFilesAfterClose().closeOnJvmShutdown().make();
        }
        else {
            this.tempFile = null;
            this.db = null;
        }
    }

    /*---------*
     * Methods *
     *---------*/

    @Override
    public boolean hasNext()
        throws QueryEvaluationException
    {
        if (!initialized) {
            synchronized (lock) {
                if (!initialized) {
                    super.setIterator(createIterator());
                    initialized = true;
                }
            }
        }
        return super.hasNext();
    }

    @Override
    public BindingSet next()
        throws QueryEvaluationException
    {
        if (!initialized) {
            synchronized (lock) {
                if (!initialized) {
                    super.setIterator(createIterator());
                    initialized = true;
                }
            }
        }
        return super.next();
    }

    @Override
    protected void handleClose()
        throws QueryEvaluationException
    {
        try {
            super.handleClose();
        }
        finally {
            if (db != null) {
                db.close();
            }
        }
    }

    private <T> Set<T> createSet(String setName) {
        if (db != null) {
            return db.getHashSet(setName);
        }
        else {
            return new HashSet<T>();
        }
    }

    private Iterator<BindingSet> createIterator()
        throws QueryEvaluationException
    {
        Collection<Entry> entries = buildEntries();
        Set<BindingSet> bindingSets = createSet("bindingsets");

        for (Entry entry : entries) {
            QueryBindingSet sol = new QueryBindingSet(parentBindings);

            for (String name : group.getGroupBindingNames()) {
                BindingSet prototype = entry.getPrototype();
                if (prototype != null) {
                    Value value = prototype.getValue(name);
                    if (value != null) {
                        // Potentially overwrites bindings from super
                        sol.setBinding(name, value);
                    }
                }
            }

            List<BindingSet> solutions = entry.getBoundSolutions(sol);

            bindingSets.addAll(solutions);
        }

        return bindingSets.iterator();
    }

    private Collection<Entry> buildEntries()
        throws QueryEvaluationException
    {
        CloseableIteration<BindingSet, QueryEvaluationException> iter;
        iter = strategy.evaluate(group.getArg(), parentBindings);

        try {
            Map<Key, Entry> entries = new LinkedHashMap<Key, Entry>();

            if (!iter.hasNext()) {
                // no solutions, but if aggregates are present we still need to process them to produce a
                // zero-result.
                final Entry entry = new Entry(null);
                if (!entry.getAggregates().isEmpty()) {
                    entry.addSolution(EmptyBindingSet.getInstance());
                    entries.put(new Key(EmptyBindingSet.getInstance()), entry);
                }
            }

            // long count = 0;

            while (iter.hasNext()) {
                BindingSet sol;
                try {
                    sol = iter.next();
                }
                catch (NoSuchElementException e) {
                    break; // closed
                }
                Key key = new Key(sol);
                Entry entry = entries.get(key);

                if (entry == null) {
                    entry = new Entry(sol);
                    entries.put(key, entry);
                }

                entry.addSolution(sol);
            }

            return entries.values();
        }
        finally {
            iter.close();
        }

    }

    /**
     * A unique key for a set of existing bindings.
     * 
     * @author David Huynh
     */
    protected class Key implements Serializable {

        private static final long serialVersionUID = 4461951265373324084L;

        private final BindingSet bindingSet;

        private final int hash;

        public Key(BindingSet bindingSet) {
            this.bindingSet = bindingSet;

            int nextHash = 0;
            for (String name : group.getGroupBindingNames()) {
                Value value = bindingSet.getValue(name);
                if (value != null) {
                    nextHash ^= value.hashCode();
                }
            }
            this.hash = nextHash;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof Key && other.hashCode() == hash) {
                BindingSet otherSolution = ((Key)other).bindingSet;

                for (String name : group.getGroupBindingNames()) {
                    Value v1 = bindingSet.getValue(name);
                    Value v2 = otherSolution.getValue(name);

                    if (!ObjectUtil.nullEquals(v1, v2)) {
                        return false;
                    }
                }

                return true;
            }

            return false;
        }
    }
    
    private class Entry {

        private final BindingSet prototype;

        private volatile Map<String, AggregateEvaluator> aggregates;

        public Entry(BindingSet prototype)
            throws ValueExprEvaluationException, QueryEvaluationException
        {
            this.prototype = prototype;

        }

        private Map<String, AggregateEvaluator> getAggregates()
            throws ValueExprEvaluationException, QueryEvaluationException
        {
            Map<String, AggregateEvaluator> result = aggregates;
            if (result == null) {
                synchronized (this) {
                    result = aggregates;
                    if (result == null) {
                        result = aggregates = new LinkedHashMap<String, AggregateEvaluator>();
                        for (GroupElem ge : group.getGroupElements()) {
                            AggregateEvaluator create = create(ge.getOperator());
                            if (create != null) {
                                aggregates.put(ge.getName(), create);
                            }
                        }
                    }
                }
            }
            return result;
        }

        public BindingSet getPrototype() {
            return prototype;
        }

        public void addSolution(BindingSet bindingSet)
            throws QueryEvaluationException
        {
            for (AggregateEvaluator aggregate : getAggregates().values()) {
                aggregate.processAggregate(bindingSet);
            }
        }

        public List<BindingSet> getBoundSolutions(QueryBindingSet sol) throws QueryEvaluationException {
            List<BindingSet> inputSolutions = Lists.<BindingSet>newArrayList(sol);
            List<BindingSet> outputSolutions = inputSolutions;
            for (String name : getAggregates().keySet()) {
                try {
                    List<Value> values = getAggregates().get(name).getValues();
                    if (values != null) {
                        // Potentially overwrites bindings from super
                        outputSolutions = Lists.newArrayList();
                        for (BindingSet inputSolution : inputSolutions) {
                            for (Value value : values) {
                                QueryBindingSet bs = copyBindingSet(inputSolution);
                                if (value != null) {
                                    bs.setBinding(name, value);
                                }
                                outputSolutions.add(bs);
                            }
                        }
                        inputSolutions = outputSolutions;
                    }
                }
                catch (ValueExprEvaluationException ex) {
                    // There was a type error when calculating the value of the
                    // aggregate.
                    // We silently ignore the error, resulting in no result value
                    // being bound.
                }
            }
            return outputSolutions;
        }
        
        
        private QueryBindingSet copyBindingSet(BindingSet original) {
            QueryBindingSet bs = new QueryBindingSet();
            for(String name : original.getBindingNames()) {
                bs.addBinding(original.getBinding(name));
            }
            return bs;
        }

        private AggregateEvaluator create(AggregateOperator operator)
            throws ValueExprEvaluationException, QueryEvaluationException
        {
            if (operator instanceof Count) {
                return new StandardAggregateEvaluator(new CountAggregate((Count)operator));
            }
            else if (operator instanceof Min) {
                return new StandardAggregateEvaluator(new MinAggregate((Min)operator));
            }
            else if (operator instanceof Max) {
                return new StandardAggregateEvaluator(new MaxAggregate((Max)operator));
            }
            else if (operator instanceof Sum) {
                return new StandardAggregateEvaluator(new SumAggregate((Sum)operator));
            }
            else if (operator instanceof Avg) {
                return new StandardAggregateEvaluator(new AvgAggregate((Avg)operator));
            }
            else if (operator instanceof Sample) {
                return new StandardAggregateEvaluator(new SampleAggregate((Sample)operator));
            }
            else if (operator instanceof GroupConcat) {
                return new StandardAggregateEvaluator(new ConcatAggregate((GroupConcat)operator));
            } else if (operator instanceof ServiceCallAggregate) {
                return new AggregateServiceEvaluator(strategy, (ServiceCallAggregate)operator);
            }
            return null;
        }
    }
    
    protected class StandardAggregateEvaluator implements AggregateEvaluator {
        
        private final Aggregate delegate;
        
        public StandardAggregateEvaluator(Aggregate delegate) {
            this.delegate = delegate;
        }

        @Override
        public void processAggregate(BindingSet s) {
            delegate.processAggregate(s);
            
        }

        @Override
        public List<Value> getValues() throws QueryEvaluationException {
            return Lists.newArrayList(delegate.getValue());
        }
        
    }
    
    private abstract class Aggregate {

        private final Set<Value> distinctValues;

        private final ValueExpr arg;

        public Aggregate(AbstractAggregateOperator operator) {
            this.arg = operator.getArg();

            if (operator.isDistinct()) {
                distinctValues = createSet("distinct-values-" + this.hashCode());
            }
            else {
                distinctValues = null;
            }
        }

        public abstract Value getValue()
            throws ValueExprEvaluationException;

        public abstract void processAggregate(BindingSet bindingSet)
            throws QueryEvaluationException;

        protected boolean distinctValue(Value value) {
            if (distinctValues == null) {
                return true;
            }

            final boolean result = distinctValues.add(value);
            if (db != null && distinctValues.size() % iterationCacheSyncThreshold == 0) {
                // write to disk every $iterationCacheSyncThreshold items
                db.commit();
            }

            return result;
        }

        protected ValueExpr getArg() {
            return arg;
        }

        protected Value evaluate(BindingSet s)
            throws QueryEvaluationException
        {
            try {
                return strategy.evaluate(getArg(), s);
            }
            catch (ValueExprEvaluationException e) {
                return null; // treat missing or invalid expressions as null
            }
        }
    }

    private class CountAggregate extends Aggregate {

        private long count = 0;

        private final Set<BindingSet> distinctBindingSets;

        public CountAggregate(Count operator) {
            super(operator);

            // for a wildcarded count with a DISTINCT clause we need to filter on
            // distinct bindingsets rather than individual values.
            if (operator.isDistinct() && getArg() == null) {
                distinctBindingSets = createSet("distinct-bs-" + this.hashCode());
            }
            else {
                distinctBindingSets = null;
            }
        }

        @Override
        public void processAggregate(BindingSet s)
            throws QueryEvaluationException
        {
            if (getArg() != null) {
                Value value = evaluate(s);
                if (value != null && distinctValue(value)) {
                    count++;
                }
            }
            else {
                // wildcard count
                if (s.size() > 0 && distinctBindingSet(s)) {
                    count++;
                }
            }
        }

        protected boolean distinctBindingSet(BindingSet s) {
            if (distinctBindingSets == null) {
                return true;
            }

            final boolean result = distinctBindingSets.add(s);
            if (db != null && distinctBindingSets.size() % iterationCacheSyncThreshold == 0) {
                // write to disk every
                db.commit();
            }

            return result;
        }

        @Override
        public Value getValue() {
            return vf.createLiteral(Long.toString(count), XMLSchema.INTEGER);
        }
    }

    private class MinAggregate extends Aggregate {

        private final ValueComparator comparator = new ValueComparator();

        private Value min = null;

        public MinAggregate(Min operator) {
            super(operator);
        }

        @Override
        public void processAggregate(BindingSet s)
            throws QueryEvaluationException
        {
            Value v = evaluate(s);
            if (v != null && distinctValue(v)) {
                if (min == null) {
                    min = v;
                }
                else if (comparator.compare(v, min) < 0) {
                    min = v;
                }
            }
        }

        @Override
        public Value getValue() {
            return min;
        }
    }

    private class MaxAggregate extends Aggregate {

        private final ValueComparator comparator = new ValueComparator();

        private Value max = null;

        public MaxAggregate(Max operator) {
            super(operator);
        }

        @Override
        public void processAggregate(BindingSet s)
            throws QueryEvaluationException
        {
            Value v = evaluate(s);
            if (v != null && distinctValue(v)) {
                if (max == null) {
                    max = v;
                }
                else if (comparator.compare(v, max) > 0) {
                    max = v;
                }
            }
        }

        @Override
        public Value getValue() {
            return max;
        }
    }

    private class SumAggregate extends Aggregate {

        private Literal sum = vf.createLiteral("0", XMLSchema.INTEGER);

        private ValueExprEvaluationException typeError = null;

        public SumAggregate(Sum operator) {
            super(operator);
        }

        @Override
        public void processAggregate(BindingSet s)
            throws QueryEvaluationException
        {
            if (typeError != null) {
                // halt further processing if a type error has been raised
                return;
            }

            Value v = evaluate(s);
            if (distinctValue(v)) {
                if (v instanceof Literal) {
                    Literal nextLiteral = (Literal)v;
                    if (nextLiteral.getDatatype() != null
                            && XMLDatatypeUtil.isNumericDatatype(nextLiteral.getDatatype()))
                    {
                        sum = MathUtil.compute(sum, nextLiteral, MathOp.PLUS);
                    }
                    else {
                        typeError = new ValueExprEvaluationException("not a number: " + v);
                    }
                }
                else if (v != null) {
                    typeError = new ValueExprEvaluationException("not a number: " + v);
                }
            }
        }

        @Override
        public Value getValue()
            throws ValueExprEvaluationException
        {
            if (typeError != null) {
                throw typeError;
            }

            return sum;
        }
    }

    private class AvgAggregate extends Aggregate {

        private long count = 0;

        private Literal sum = vf.createLiteral("0", XMLSchema.INTEGER);

        private ValueExprEvaluationException typeError = null;

        public AvgAggregate(Avg operator) {
            super(operator);
        }

        @Override
        public void processAggregate(BindingSet s)
            throws QueryEvaluationException
        {
            if (typeError != null) {
                // Prevent calculating the aggregate further if a type error has
                // occured.
                return;
            }

            Value v = evaluate(s);
            if (distinctValue(v)) {
                if (v instanceof Literal) {
                    Literal nextLiteral = (Literal)v;
                    // check if the literal is numeric.
                    if (nextLiteral.getDatatype() != null
                            && XMLDatatypeUtil.isNumericDatatype(nextLiteral.getDatatype()))
                    {
                        sum = MathUtil.compute(sum, nextLiteral, MathOp.PLUS);
                    }
                    else {
                        typeError = new ValueExprEvaluationException("not a number: " + v);
                    }
                    count++;
                }
                else if (v != null) {
                    // we do not actually throw the exception yet, but record it and
                    // stop further processing. The exception will be thrown when
                    // getValue() is invoked.
                    typeError = new ValueExprEvaluationException("not a number: " + v);
                }
            }
        }

        @Override
        public Value getValue()
            throws ValueExprEvaluationException
        {
            if (typeError != null) {
                // a type error occurred while processing the aggregate, throw it
                // now.
                throw typeError;
            }

            if (count == 0) {
                return vf.createLiteral(0.0d);
            }

            Literal sizeLit = vf.createLiteral(count);
            return MathUtil.compute(sum, sizeLit, MathOp.DIVIDE);
        }
    }

    private class SampleAggregate extends Aggregate {

        private Value sample = null;

        private Random random;

        public SampleAggregate(Sample operator) {
            super(operator);
            random = new Random(System.currentTimeMillis());
        }

        @Override
        public void processAggregate(BindingSet s)
            throws QueryEvaluationException
        {
            // we flip a coin to determine if we keep the current value or set a
            // new value to report.
            if (sample == null || random.nextFloat() < 0.5f) {
                final Value newValue = evaluate(s);
                if (newValue != null) {
                    sample = newValue;
                }
            }
        }

        @Override
        public Value getValue() {
            return sample;
        }
    }

    private class ConcatAggregate extends Aggregate {

        private StringBuilder concatenated = new StringBuilder();

        private String separator = " ";

        public ConcatAggregate(GroupConcat groupConcatOp)
            throws ValueExprEvaluationException, QueryEvaluationException
        {
            super(groupConcatOp);
            ValueExpr separatorExpr = groupConcatOp.getSeparator();
            if (separatorExpr != null) {
                Value separatorValue = strategy.evaluate(separatorExpr, parentBindings);
                separator = separatorValue.stringValue();
            }
        }

        @Override
        public void processAggregate(BindingSet s)
            throws QueryEvaluationException
        {
            Value v = evaluate(s);
            if (v != null && distinctValue(v)) {
                concatenated.append(v.stringValue());
                concatenated.append(separator);
            }
        }

        @Override
        public Value getValue() {
            if (concatenated.length() == 0) {
                return vf.createLiteral("");
            }

            // remove separator at the end.
            int len = concatenated.length() - separator.length();
            return vf.createLiteral(concatenated.substring(0, len));
        }
    }
}