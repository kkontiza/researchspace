/*
 * Copyright (C) 2015-2017, metaphacts GmbH
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

import { assert } from 'chai';

import { Rdf, vocabularies } from 'platform/api/rdf';
import {
  ChartType, DataSetMappings, ChartProvider,
} from '../../../../main/components/semantic/chart/ChartingCommons';
import {
  SemanticChartProps, buildData,
} from '../../../../main/components/semantic/chart/SemanticChart';

const { literal, iri} = Rdf;
const { xsd } = vocabularies;

const NON_PIVOTING_DATASETS = {
  'head' : {
    'link' : [],
    'vars' : ['year', 'papers', 'authors', 'authorsPerPaper'],
  },
  'results' : {
    'distinct' : undefined,
    'ordered' : undefined,
    'bindings' : [
      {
        'year': literal('2008'),
        'papers': literal('149', xsd.integer),
        'authors': literal('396', xsd.integer),
        'authorsPerPaper': literal('2.6577181208053691275', xsd.decimal),
      },
      {
        'year': literal('2009'),
        'papers': literal('118', xsd.integer),
        'authors': literal('358', xsd.integer),
        'authorsPerPaper': literal('3.03389830508474576271', xsd.decimal),
      },
    ],
  },
};

const peter = iri('http://data.semanticweb.org/person/peter-haase');
const enrico = iri('http://data.semanticweb.org/person/enrico-motta');
const PIVOTING_DATASETS = {
  'head' : {
    'link' : [],
    'vars' : [ 'author', 'year', 'papers' ],
  },
  'results' : {
    'distinct' : undefined,
    'ordered' : undefined,
    'bindings' : [ {
      'author': peter, 'year': literal('2010'), 'papers': literal('2', xsd.integer),
    }, {
      'author': peter, 'year': literal('2012'), 'papers': literal('1', xsd.integer),
    }, {
      'author' : enrico, 'year' : literal('2012'), 'papers' : literal('2', xsd.integer),
    }, {
      'author' : peter, 'year' : literal('2013'), 'papers' : literal('7', xsd.integer),
    }, {
      'author' : enrico, 'year' : literal('2013'), 'papers' : literal('2', xsd.integer),
    }],
  },
};

function chartConfig(config: {
    type: ChartType,
    dataSets?: DataSetMappings[],
    multiDataSet?: DataSetMappings,
    provider: ChartProvider,
  }): SemanticChartProps {
    return {
      type: config.type,
      sets: config.dataSets,
      multiDataSet: config.multiDataSet,
      query: '',
      provider: config.provider,
    };
}

describe('ChartWidgetComponent', () => {
  it('builds categorial data without pivoting (highcharts)', () => {
    const props = chartConfig({
      type: 'bar',
      dataSets: [
        { dataSetName: 'Authors', category: 'year', value: 'authors' },
        { dataSetName: 'Papers', category: 'year', value: 'papers' },
      ],
      provider: 'highcharts',
    });
    const builtData = buildData(props, NON_PIVOTING_DATASETS);
    const expectedData = {
      'sets': [
        {
          'mapping': {
            'dataSetName': 'Authors',
            'category': 'year',
            'value': 'authors',
          },
          'iri': undefined,
          'name': 'Authors',
          'points': NON_PIVOTING_DATASETS.results.bindings,
        },
        {
          'mapping': {
            'dataSetName': 'Papers',
            'category': 'year',
            'value': 'papers',
          },
          'iri': undefined,
          'name': 'Papers',
          'points': NON_PIVOTING_DATASETS.results.bindings,
        },
      ],
      'categories': [
        literal('2008'), literal('2009'),
      ],
    };

    assert.deepEqual<any>(builtData, expectedData);
  });

  it('builds categorial data with pivoting (chartjs)', () => {
    const props = chartConfig({
      type: 'bar',
      multiDataSet: { dataSetVariable: 'author', category: 'year', value: 'papers' },
      provider: 'chartjs',
    });
    const builtData = buildData(props, PIVOTING_DATASETS);
    const expectedData = {
      'sets': [
        {
          'id': 'http://data.semanticweb.org/person/enrico-motta',
          'iri': enrico,
          'name': null as string,
          'mapping': {
            'dataSetVariable': 'author',
            'category': 'year',
            'value': 'papers',
          },
          'points': [
            null,
            {
              'author': enrico, 'year': literal('2012'), 'papers': literal('2', xsd.integer),
            },
            {
              'author' : enrico, 'year' : literal('2013'), 'papers' : literal('2', xsd.integer),
            },
          ],
        },
        {
          'id': 'http://data.semanticweb.org/person/peter-haase',
          'iri': peter,
          'name': null as string,
          'mapping': {
            'dataSetVariable': 'author',
            'category': 'year',
            'value': 'papers',
          },
          'points': [
            {
              'author': peter, 'year': literal('2010'), 'papers': literal('2', xsd.integer),
            },
            {
              'author': peter, 'year': literal('2012'), 'papers': literal('1', xsd.integer),
            }, {
              'author' : peter, 'year' : literal('2013'), 'papers' : literal('7', xsd.integer),
            },
          ],
        },
      ],
      'categories': [
        literal('2010'), literal('2012'), literal('2013'),
      ],
    };

    assert.deepEqual<any>(builtData, expectedData);
  });
});
