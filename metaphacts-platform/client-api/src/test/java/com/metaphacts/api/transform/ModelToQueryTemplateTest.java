/*
 * Copyright (C) 2015-2016, metaphacts GmbH
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
package com.metaphacts.api.transform;


import static com.metaphacts.api.transform.ModelUtils.getNotNullSubjectIRI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.SP;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import com.metaphacts.api.dto.query.Query;
import com.metaphacts.api.dto.querytemplate.QueryArgument;
import com.metaphacts.api.dto.querytemplate.QueryTemplate;

public class ModelToQueryTemplateTest extends BaseModelToDtoTest {
    
    @Test
    public void testModelToTemplateTest() throws Exception{
        Model queryModel = readTurtleInputStreamIntoModel(FILE_SPIN_QUERY_TTL, vf.createIRI("http://www.test.de"));
        IRI queryIRI = getNotNullSubjectIRI(queryModel, RDF.TYPE, SP.QUERY_CLASS);
        Query<?> query = new ModelToQueryTransformer().modelToDto(queryModel);        
        assertNotNull(query);
        
        Model templateModel = readTurtleInputStreamIntoModel(FILE_SPIN_TEMPLATE_TTL, vf.createIRI("http://www.test.de"));
        QueryTemplate<?> template = new ModelToQueryTemplateTransformer( new QueryCatalogTestAPI(query)).modelToDto(templateModel);
    
        assertEquals(vf.createIRI("http://www.test.de/queryTemplate"),template.getId());
        assertEquals("Persons that know another a particular person.",template.getLabel());
        assertEquals("Selects persons having the exact ?name and know the given ?friend .",template.getDescription());
        assertEquals("Person with the name {?name} that know {?friend}",template.getLabelTemplate());
        assertEquals(queryIRI, template.getQuery().getId());
        assertEquals(2, template.getArguments().size());
       
        /* Test query arguments */
        QueryArgument arg1 = template.getArgument(vf.createIRI("http://www.test.de/arg1"));
        QueryArgument arg2 = template.getArgument(vf.createIRI("http://www.test.de/arg2"));
        assertNotNull(arg1);
        assertNotNull(arg2);
        
        //1st is explicitly not required
        assertFalse(arg1.isRequired());
        //2nd has no required specified and as such should be required by default
        assertTrue(arg2.isRequired());
        
        assertEquals("The friend to be known.",arg1.getLabel());
        assertEquals("The name of the person.",arg2.getLabel());
       
        assertEquals(
            "Argument to bind the ?friend variable of the query.\nThe value of the argument must be of type xsd:Resource.",
            arg1.getDescription().replace("\r\n", "\n")
        );
        assertEquals(
            "Argument to bind the ?name variable of the query.\nThe value of the argument must be of type xsd:string.",
            arg2.getDescription().replace("\r\n", "\n")
        );

        assertEquals("friend",arg1.getPredicate());
        assertEquals("name",arg2.getPredicate());
        
        assertEquals("friend",arg1.getPredicate());
        assertEquals("name",arg2.getPredicate());
        
        assertEquals(RDFS.RESOURCE,arg1.getValueType());
        assertEquals(XMLSchema.STRING,arg2.getValueType());
        
    }

}
