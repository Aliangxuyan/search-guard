/*
 * Copyright 2015-2017 floragunn GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.floragunn.searchguard;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.http.HttpStatus;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.InvalidIndexNameException;
import org.elasticsearch.indices.InvalidTypeNameException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.floragunn.searchguard.support.ConfigConstants;
import com.floragunn.searchguard.test.DynamicSgConfig;
import com.floragunn.searchguard.test.SingleClusterTest;
import com.floragunn.searchguard.test.helper.rest.RestHelper;
import com.floragunn.searchguard.test.helper.rest.RestHelper.HttpResponse;

public class IndexIntegrationTests extends SingleClusterTest {

    @Test
    public void testComposite() throws Exception {
    
        setup(Settings.EMPTY, new DynamicSgConfig().setSgConfig("sg_composite_config.yml").setSgRoles("sg_roles_composite.yml"), Settings.EMPTY, true);
        final RestHelper rh = nonSslRestHelper();
    
        try (TransportClient tc = getInternalTransportClient()) {                
            tc.index(new IndexRequest("starfleet").type("ships").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();           
            tc.index(new IndexRequest("klingonempire").type("ships").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();      
            tc.index(new IndexRequest("public").type("legends").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();            
        }
        
        String msearchBody = 
                "{\"index\":\"starfleet\", \"type\":\"ships\", \"ignore_unavailable\": true}"+System.lineSeparator()+
                "{\"size\":10, \"query\":{\"bool\":{\"must\":{\"match_all\":{}}}}}"+System.lineSeparator()+
                "{\"index\":\"klingonempire\", \"type\":\"ships\", \"ignore_unavailable\": true}"+System.lineSeparator()+
                "{\"size\":10, \"query\":{\"bool\":{\"must\":{\"match_all\":{}}}}}"+System.lineSeparator()+
                "{\"index\":\"public\", \"ignore_unavailable\": true}"+System.lineSeparator()+
                "{\"size\":10, \"query\":{\"bool\":{\"must\":{\"match_all\":{}}}}}"+System.lineSeparator();
                         
            
        HttpResponse resc = rh.executePostRequest("_msearch", msearchBody, encodeBasicHeader("worf", "worf"));
        Assert.assertEquals(200, resc.getStatusCode());
        Assert.assertTrue(resc.getBody(), resc.getBody().contains("\"_index\":\"klingonempire\""));
        Assert.assertTrue(resc.getBody(), resc.getBody().contains("hits"));
        Assert.assertTrue(resc.getBody(), resc.getBody().contains("no permissions for [indices:data/read/search]"));
        
    }
    
    @Test
    public void testBulkShards() throws Exception {
    
        setup(Settings.EMPTY, new DynamicSgConfig().setSgRoles("sg_roles_bs.yml"), Settings.EMPTY, true);
        final RestHelper rh = nonSslRestHelper();
        
        try (TransportClient tc = getInternalTransportClient()) {               
            //create indices and mapping upfront
            tc.index(new IndexRequest("test").type("type1").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"field2\":\"init\"}", XContentType.JSON)).actionGet();           
            tc.index(new IndexRequest("lorem").type("type1").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"field2\":\"init\"}", XContentType.JSON)).actionGet();      
        }
        
        String bulkBody = 
        "{ \"index\" : { \"_index\" : \"test\", \"_type\" : \"type1\", \"_id\" : \"1\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value1\" }" +System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"test\", \"_type\" : \"type1\", \"_id\" : \"2\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"test\", \"_type\" : \"type1\", \"_id\" : \"3\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"test\", \"_type\" : \"type1\", \"_id\" : \"4\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"test\", \"_type\" : \"type1\", \"_id\" : \"5\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"lorem\", \"_type\" : \"type1\", \"_id\" : \"1\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"lorem\", \"_type\" : \"type1\", \"_id\" : \"2\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"lorem\", \"_type\" : \"type1\", \"_id\" : \"3\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"lorem\", \"_type\" : \"type1\", \"_id\" : \"4\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"index\" : { \"_index\" : \"lorem\", \"_type\" : \"type1\", \"_id\" : \"5\" } }"+System.lineSeparator()+
        "{ \"field2\" : \"value2\" }"+System.lineSeparator()+
        "{ \"delete\" : { \"_index\" : \"lorem\", \"_type\" : \"type1\", \"_id\" : \"5\" } }"+System.lineSeparator();
       
        System.out.println("############ _bulk");
        HttpResponse res = rh.executePostRequest("_bulk?refresh=true&pretty=true", bulkBody, encodeBasicHeader("worf", "worf"));
        System.out.println(res.getBody());
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());  
        Assert.assertTrue(res.getBody().contains("\"errors\" : true"));
        Assert.assertTrue(res.getBody().contains("\"status\" : 201"));
        Assert.assertTrue(res.getBody().contains("no permissions for"));
        
        System.out.println("############ check shards");
        System.out.println(rh.executeGetRequest("_cat/shards?v", encodeBasicHeader("nagilum", "nagilum")));

        
    }

    @Test
    public void testCreateIndex() throws Exception {
    
        setup();
        RestHelper rh = nonSslRestHelper();
              
        HttpResponse res;
        Assert.assertEquals("Unable to create index 'nag'", HttpStatus.SC_OK, rh.executePutRequest("nag1", null, encodeBasicHeader("nagilum", "nagilum")).getStatusCode());
        Assert.assertEquals("Unable to create index 'starfleet_library'", HttpStatus.SC_OK, rh.executePutRequest("starfleet_library", null, encodeBasicHeader("nagilum", "nagilum")).getStatusCode());
        
        clusterHelper.waitForCluster(ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(10), clusterInfo.numNodes);
        
        Assert.assertEquals("Unable to close index 'starfleet_library'", HttpStatus.SC_OK, rh.executePostRequest("starfleet_library/_close", null, encodeBasicHeader("nagilum", "nagilum")).getStatusCode());
        
        Assert.assertEquals("Unable to open index 'starfleet_library'", HttpStatus.SC_OK, (res = rh.executePostRequest("starfleet_library/_open", null, encodeBasicHeader("nagilum", "nagilum"))).getStatusCode());
        Assert.assertTrue("open index 'starfleet_library' not acknowledged", res.getBody().contains("acknowledged"));
        Assert.assertFalse("open index 'starfleet_library' not acknowledged", res.getBody().contains("false"));
        
        clusterHelper.waitForCluster(ClusterHealthStatus.GREEN, TimeValue.timeValueSeconds(10), clusterInfo.numNodes);
        
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, rh.executePutRequest("public", null, encodeBasicHeader("spock", "spock")).getStatusCode());
        
        
    }

    @Test
    public void testFilteredAlias() throws Exception {
    
        setup();
        
        try (TransportClient tc = getInternalTransportClient()) {

            tc.index(new IndexRequest("theindex").type("type1").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("otherindex").type("type1").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.admin().indices().aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().alias("alias1").filter(QueryBuilders.termQuery("_type", "type1")).index("theindex"))).actionGet();
            tc.admin().indices().aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().alias("alias2").filter(QueryBuilders.termQuery("_type", "type2")).index("theindex"))).actionGet();
            tc.admin().indices().aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().alias("alias3").filter(QueryBuilders.termQuery("_type", "type2")).index("otherindex"))).actionGet();
        }
        
        
        RestHelper rh = nonSslRestHelper();
    
        //sg_user1 -> worf
        //sg_user2 -> picard
        
        HttpResponse resc = rh.executeGetRequest("alias*/_search", encodeBasicHeader("worf", "worf"));
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, resc.getStatusCode());
        
        resc =  rh.executeGetRequest("theindex/_search", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, resc.getStatusCode());
        
        resc =  rh.executeGetRequest("alias3/_search", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, resc.getStatusCode());

        resc =  rh.executeGetRequest("_cat/indices", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, resc.getStatusCode());
        
    }
    
    @Test
    public void testIndexTypeEvaluation() throws Exception {
    
        setup();
    
        try (TransportClient tc = getInternalTransportClient()) {          
            tc.index(new IndexRequest("foo1").type("bar").id("1").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("foo2").type("bar").id("2").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":2}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("foo").type("baz").id("3").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":3}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("fooba").type("z").id("4").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":4}", XContentType.JSON)).actionGet();
            
            try {
                tc.index(new IndexRequest("x#a").type("xxx").id("4a").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":4}", XContentType.JSON)).actionGet();
                Assert.fail("Indexname can contain #");
            } catch (InvalidIndexNameException e) {
                //expected
            }
            
            
            try {
                tc.index(new IndexRequest("xa").type("x#a").id("4a").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":4}", XContentType.JSON)).actionGet();
                Assert.fail("Typename can contain #");
            } catch (InvalidTypeNameException e) {
                //expected
            }
        }
        
        RestHelper rh = nonSslRestHelper();
    
        HttpResponse  resc = rh.executeGetRequest("/foo1/bar/_search?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_OK, resc.getStatusCode());
        Assert.assertTrue(resc.getBody().contains("\"content\" : 1"));
        
        resc = rh.executeGetRequest("/foo2/bar/_search?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_OK, resc.getStatusCode());
        Assert.assertTrue(resc.getBody().contains("\"content\" : 2"));
        
        resc = rh.executeGetRequest("/foo/baz/_search?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_OK, resc.getStatusCode());
        Assert.assertTrue(resc.getBody().contains("\"content\" : 3"));
        
        resc = rh.executeGetRequest("/fooba/z/_search?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, resc.getStatusCode());        
    
        resc = rh.executeGetRequest("/foo1/bar/1?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_OK, resc.getStatusCode());
        Assert.assertTrue(resc.getBody().contains("\"found\" : true"));
        Assert.assertTrue(resc.getBody().contains("\"content\" : 1"));
        
        resc = rh.executeGetRequest("/foo2/bar/2?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_OK, resc.getStatusCode());
        Assert.assertTrue(resc.getBody().contains("\"content\" : 2"));
        Assert.assertTrue(resc.getBody().contains("\"found\" : true"));
        
        resc = rh.executeGetRequest("/foo/baz/3?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_OK, resc.getStatusCode());
        Assert.assertTrue(resc.getBody().contains("\"content\" : 3"));
        Assert.assertTrue(resc.getBody().contains("\"found\" : true"));
    
        resc = rh.executeGetRequest("/fooba/z/4?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, resc.getStatusCode());
    
        resc = rh.executeGetRequest("/foo*/_search?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, resc.getStatusCode());
    
        resc = rh.executeGetRequest("/foo*,-fooba/bar/_search?pretty", encodeBasicHeader("baz", "worf"));
        Assert.assertEquals(200, resc.getStatusCode());
        Assert.assertTrue(resc.getBody().contains("\"content\" : 1"));
        Assert.assertTrue(resc.getBody().contains("\"content\" : 2"));
    }

    @Test
    public void testIndices() throws Exception {
    
        setup();
    
        try (TransportClient tc = getInternalTransportClient()) {
            tc.index(new IndexRequest("nopermindex").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
    
            tc.index(new IndexRequest("logstash-1").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-2").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-3").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-4").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
    
            SimpleDateFormat sdf = new SimpleDateFormat("YYYY.MM.dd");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            
            String date = sdf.format(new Date());
            tc.index(new IndexRequest("logstash-"+date).type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
        }
        
        RestHelper rh = nonSslRestHelper();
        
        HttpResponse res = null;
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-1/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
    
        //nonexistent index with permissions
        Assert.assertEquals(HttpStatus.SC_NOT_FOUND, (res = rh.executeGetRequest("/logstash-nonex/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
    
        //existent index without permissions
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/nopermindex/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());

        //nonexistent index without permissions
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/does-not-exist-and-no-perm/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
    
        //existent index with permissions
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-1/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());

        //nonexistent index with failed login
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, (res = rh.executeGetRequest("/logstash-nonex/_search", encodeBasicHeader("nouser", "nosuer"))).getStatusCode());   
        
        //nonexistent index with no login
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, (res = rh.executeGetRequest("/logstash-nonex/_search")).getStatusCode());   
        
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
        
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/_all/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
    
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/*/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());        
    
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/nopermindex,logstash-1,nonexist/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
        
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/logstash-1,nonexist/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
        
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/nonexist/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/%3Clogstash-%7Bnow%2Fd%7D%3E/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
    
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/%3Cnonex-%7Bnow%2Fd%7D%3E/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/%3Clogstash-%7Bnow%2Fd%7D%3E,logstash-*/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
        
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/%3Clogstash-%7Bnow%2Fd%7D%3E,logstash-1/_search", encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
        
        Assert.assertEquals(HttpStatus.SC_CREATED, (res = rh.executePutRequest("/logstash-b/logs/1", "{}",encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
    
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePutRequest("/%3Clogstash-cnew-%7Bnow%2Fd%7D%3E", "{}",encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
        
        Assert.assertEquals(HttpStatus.SC_CREATED, (res = rh.executePutRequest("/%3Clogstash-new-%7Bnow%2Fd%7D%3E/logs/1", "{}",encodeBasicHeader("logstash", "nagilum"))).getStatusCode());
    
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/_cat/indices?v" ,encodeBasicHeader("nagilum", "nagilum"))).getStatusCode());
    
        System.out.println(res.getBody());
        Assert.assertTrue(res.getBody().contains("logstash-b"));
        Assert.assertTrue(res.getBody().contains("logstash-new-20"));
        Assert.assertTrue(res.getBody().contains("logstash-cnew-20"));
        Assert.assertFalse(res.getBody().contains("<"));
    }
    
    @Test
    public void testAliases() throws Exception {

        final Settings settings = Settings.builder()
                .put(ConfigConstants.SEARCHGUARD_ROLES_MAPPING_RESOLUTION, "BOTH")
                .build();

        setup(settings);
    
        try (TransportClient tc = getInternalTransportClient()) {
            tc.index(new IndexRequest("nopermindex").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
    
            tc.index(new IndexRequest("logstash-1").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-2").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-3").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-4").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-5").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-del").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("logstash-del-ok").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();

            String date = new SimpleDateFormat("YYYY.MM.dd").format(new Date());
            tc.index(new IndexRequest("logstash-"+date).type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
        
            tc.admin().indices().aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("nopermindex").alias("nopermalias"))).actionGet();
            tc.admin().indices().aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("searchguard").alias("mysgi"))).actionGet();
        }
        
        RestHelper rh = nonSslRestHelper();
        
        HttpResponse res = null;
        
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executePostRequest("/mysgi/sg", "{}",encodeBasicHeader("nagilum", "nagilum"))).getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/mysgi/_search?pretty", encodeBasicHeader("nagilum", "nagilum"))).getStatusCode());
        assertContains(res, "*\"hits\" : {*\"total\" : 0,*\"hits\" : [ ]*");
        
        System.out.println("#### add alias to allowed index");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executePutRequest("/logstash-1/_alias/alog1", "",encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());

        System.out.println("#### add alias to not existing (no perm)");
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executePutRequest("/nonexitent/_alias/alnp", "",encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());
        
        System.out.println("#### add alias to not existing (with perm)");
        Assert.assertEquals(HttpStatus.SC_NOT_FOUND, (res = rh.executePutRequest("/logstash-nonex/_alias/alnp", "",encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());
        
        System.out.println("#### add alias to not allowed index");
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executePutRequest("/nopermindex/_alias/alnp", "",encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());

        String aliasRemoveIndex = "{"+
            "\"actions\" : ["+
               "{ \"add\":  { \"index\": \"logstash-del-ok\", \"alias\": \"logstash-del\" } },"+
               "{ \"remove_index\": { \"index\": \"logstash-del\" } }  "+
            "]"+
        "}";
        
        System.out.println("#### remove_index");
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executePostRequest("/_aliases", aliasRemoveIndex,encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());

        
        System.out.println("#### get alias for permitted index");
        Assert.assertEquals(HttpStatus.SC_OK, (res = rh.executeGetRequest("/logstash-1/_alias/alog1", encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());

        
        System.out.println("#### get alias for all indices");
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/_alias/alog1", encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());

        
        System.out.println("#### get alias no perm");
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executeGetRequest("/_alias/nopermalias", encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());
        
        String alias =
        "{"+
          "\"aliases\": {"+
            "\"alias1\": {}"+
          "}"+
        "}";
        
        
        System.out.println("#### create alias along with index");
        Assert.assertEquals(HttpStatus.SC_FORBIDDEN, (res = rh.executePutRequest("/beats-withalias", alias,encodeBasicHeader("aliasmngt", "nagilum"))).getStatusCode());        
    }

    @Test
    public void testAliasResolution() throws Exception {

        final Settings settings = Settings.builder()
                .build();
        setup(settings);
        final RestHelper rh = nonSslRestHelper();

        try (TransportClient tc = getInternalTransportClient()) {                    
            tc.index(new IndexRequest("concreteindex-1").type("doc").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();                
            tc.admin().indices().aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices("concreteindex-1").alias("calias-1"))).actionGet();
            tc.index(new IndexRequest(".kibana-6").type("doc").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();                
            tc.admin().indices().aliases(new IndicesAliasesRequest().addAliasAction(AliasActions.add().indices(".kibana-6").alias(".kibana"))).actionGet();

        }

        Assert.assertEquals(HttpStatus.SC_OK, rh.executeGetRequest("calias-1/_search?pretty", encodeBasicHeader("aliastest", "nagilum")).getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, rh.executeGetRequest("calias-*/_search?pretty", encodeBasicHeader("aliastest", "nagilum")).getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, rh.executeGetRequest("*kibana/_search?pretty", encodeBasicHeader("aliastest", "nagilum")).getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, rh.executeGetRequest(".ki*ana/_search?pretty", encodeBasicHeader("aliastest", "nagilum")).getStatusCode());
        Assert.assertEquals(HttpStatus.SC_OK, rh.executeGetRequest(".kibana/_search?pretty", encodeBasicHeader("aliastest", "nagilum")).getStatusCode());
    }
    
    @Test
    public void testCCSIndexResolve() throws Exception {
        
        setup();
        final RestHelper rh = nonSslRestHelper();

        try (TransportClient tc = getInternalTransportClient()) {                                       
            tc.index(new IndexRequest(".abc-6").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
        }
        
        HttpResponse res = rh.executeGetRequest("/*:.abc-6,.abc-6/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertTrue(res.getBody(),res.getBody().contains("\"content\":1"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
    }

    @Test
    @Ignore
    public void testCCSIndexResolve2() throws Exception {
        
        setup();
        final RestHelper rh = nonSslRestHelper();

        try (TransportClient tc = getInternalTransportClient()) {                                       
            tc.index(new IndexRequest(".abc").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":1}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("xyz").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":2}", XContentType.JSON)).actionGet();
            tc.index(new IndexRequest("noperm").type("logs").setRefreshPolicy(RefreshPolicy.IMMEDIATE).source("{\"content\":3}", XContentType.JSON)).actionGet();

        }
        
        HttpResponse res = rh.executeGetRequest("/*:.abc,.abc/_search", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
        Assert.assertTrue(res.getBody(),res.getBody().contains("\"content\":1"));
        
        res = rh.executeGetRequest("/ba*bcuzh/_search", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertTrue(res.getBody(),res.getBody().contains("\"content\":12"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
        
        res = rh.executeGetRequest("/*:.abc/_search", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertTrue(res.getBody(),res.getBody().contains("\"content\":1"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
        
        res = rh.executeGetRequest("/*:xyz,xyz/_search", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
        Assert.assertTrue(res.getBody(),res.getBody().contains("\"content\":2"));
        
        //res = rh.executeGetRequest("/*noexist/_search", encodeBasicHeader("nagilum", "nagilum"));
        //Assert.assertEquals(HttpStatus.SC_NOT_FOUND, res.getStatusCode()); 
        
        res = rh.executeGetRequest("/*:.abc/_search", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode()); 
        Assert.assertTrue(res.getBody(),res.getBody().contains("\"content\":1"));
        
        res = rh.executeGetRequest("/*:xyz/_search", encodeBasicHeader("nagilum", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode()); 
        Assert.assertTrue(res.getBody(),res.getBody().contains("\"content\":2"));
   
        res = rh.executeGetRequest("/.abc/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());  
        res = rh.executeGetRequest("/xyz/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());  
        res = rh.executeGetRequest("/*:.abc,.abc/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());  
        res = rh.executeGetRequest("/*:xyz,xyz/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
        res = rh.executeGetRequest("/*:.abc/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode()); 
        res = rh.executeGetRequest("/*:xyz/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode()); 
        res = rh.executeGetRequest("/*:noperm/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
        res = rh.executeGetRequest("/*:noperm/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
        System.out.println(res.getBody());
        res = rh.executeGetRequest("/*:noexists/_search", encodeBasicHeader("ccsresolv", "nagilum"));
        Assert.assertEquals(HttpStatus.SC_OK, res.getStatusCode());
        System.out.println(res.getBody());
    }
}
