/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.safehaus.core;

import org.elasticsearch.action.search.SearchResponse;

import java.util.Map;

/**
 * ...
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class StatisticResponse {
    private SearchResponse searchResponse;
    private int responseCount;
    private Timestamp [] timestamps;
    private Number [] values;
    private String units;

    public StatisticResponse(SearchResponse searchResponse, String value)
    {
        this.setSearchResponse(searchResponse);
        if(searchResponse != null)
            setResponseCount(searchResponse.getHits().getHits().length);
        else
            responseCount = 0;
        setTimestamps(new Timestamp[responseCount]);
        values = new Number[responseCount];

        Object temp = null;
        if(responseCount != 0)
            temp = searchResponse.getHits().getHits()[0].getSource().get("units");
        if( temp != null)
            units = temp.toString();
        else
            units = "";
        for(int i = 0; i < responseCount; i++)
        {
            Map map = searchResponse.getHits().getHits()[i].getSource();
            getTimestamps()[i] = new Timestamp(map.get("@timestamp").toString());
            if(map.get(value)!=null)
            {
                Double deneme = (Double.parseDouble(map.get(value).toString()));
                getValues()[i] = Double.valueOf(deneme).longValue();
            }
        }
    }

    public SearchResponse getSearchResponse() {
        return searchResponse;
    }

    public void setSearchResponse(SearchResponse searchResponse) {
        this.searchResponse = searchResponse;
    }

    public int getResponseCount() {
        return responseCount;
    }

    public void setResponseCount(int responseCount) {
        this.responseCount = responseCount;
    }

    public Number[] getValues() {
        return values;
    }

    public void setValues(Number[] values) {
        this.values = values;
    }

    public Timestamp[] getTimestamps() {
        return timestamps;
    }

    public void setTimestamps(Timestamp[] timestamps) {
        this.timestamps = timestamps;
    }

    public String getUnits() {
        return units;
    }

    public void setUnits(String units) {
        this.units = units;
    }
}
