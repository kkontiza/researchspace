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

package com.metaphacts.config.groups;

import com.metaphacts.config.ConfigurationParameter;
import com.metaphacts.config.InvalidConfigurationException;
import com.metaphacts.services.storage.api.PlatformStorage;

import javax.inject.Inject;

public class DataQualityConfiguration extends ConfigurationGroupBase {

    private static final String ID = "dataQuality";
    private static final String DESCRIPTION =
            "Configuration options that affect execution of data quality checks.";

    @Inject
    public DataQualityConfiguration(PlatformStorage platformStorage) throws InvalidConfigurationException {
        super(ID, DESCRIPTION, platformStorage);
    }
    
    /**
     * Type of SHACL engine to be used.
     * 
     * @default rdfunit
     */
    @ConfigurationParameter
    public String getShaclEngine() {
        return getString("shaclEngine", "rdfunit");
    }
    
    /**
     * Query delay in ms between SPARQL requests send by data quality engine.
     * 
     * @default 0
     */
    @ConfigurationParameter
    public Integer getQueryDelay() {
        return getInteger("queryDelay", 0);
    }
    
    /**
     * Max number of violations that is captured in violation report.
     * This limit will be applied to all SPARQL queries send/generated by data quality engine.
     * 
     * @default 100
     */
    @ConfigurationParameter
    public Integer getQueryLimit() {
        return getInteger("queryLimit", 100);
    }
    
    /**
     * If violation results need to be requested page by page (applies LIMIT ... OFFSET  ..),
     * this number specifies size of the pages.
     * 
     * @default 0
     */
    @ConfigurationParameter
    public Integer getPagination() {
        return getInteger("pagination", 0);
    }
    
    @Override
    public void assertConsistency() {
        // TODO Auto-generated method stub
    }
}
