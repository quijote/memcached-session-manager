/*
 * Copyright 2009 Martin Grotzke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an &quot;AS IS&quot; BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package de.javakaffee.web.msm.integration;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Embedded;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HeaderElement;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;

import de.javakaffee.web.msm.MemcachedBackupSessionManager;

/**
 * Integration test utils.
 * 
 * @author <a href="mailto:martin.grotzke@javakaffee.de">Martin Grotzke</a>
 */
public class TestUtils {
    
    public static String makeRequest( final HttpClient client, int port, String rsessionId ) throws IOException,
            HttpException {
        System.out.println( port + " >>>>>>>>>>>>>>>>>> Starting >>>>>>>>>>>>>>>>>>>>");
        String responseSessionId;
        final HttpMethod method = new GetMethod("http://localhost:"+ port +"/");
        try {
            if ( rsessionId != null ) {
                method.setRequestHeader( "Cookie", "JSESSIONID=" + rsessionId );
            }
            
            System.out.println( "cookies: " + method.getParams().getCookiePolicy() );
            //method.getParams().setCookiePolicy(CookiePolicy.RFC_2109);
            client.executeMethod( method );
            System.out.println( ">>>>>>>>>>: " + method.getResponseBodyAsString() );
            responseSessionId = getSessionIdFromResponse( method );
            System.out.println( "response cookie: " + responseSessionId );
            
            return responseSessionId;
            
        } finally {
            method.releaseConnection();
            System.out.println( port + " <<<<<<<<<<<<<<<<<<<<<< Finished <<<<<<<<<<<<<<<<<<<<<<<");
        }
    }

    public static String getSessionIdFromResponse( final HttpMethod method ) {
        final Header cookie = method.getResponseHeader( "Set-Cookie" );
        if ( cookie != null ) {
            for ( HeaderElement header : cookie.getElements() ) {
                if ( "JSESSIONID".equals( header.getName() ) ) {
                    return header.getValue();
                }
            }
        }
        return null;
    }

    public static Embedded createCatalina( final int port, String memcachedNodes ) throws MalformedURLException,
            UnknownHostException, LifecycleException {
        final Embedded catalina = new Embedded();
        final Engine engine = catalina.createEngine();
        /* we must have a unique name for mbeans
         */
        engine.setName( "engine-" + port );
        engine.setDefaultHost( "localhost" );
        
        final URL root = new URL( TestUtils.class.getResource( "/" ), "../resources" );
        
        final String docBase = root.getFile() + File.separator + TestUtils.class.getPackage().getName().replaceAll( "\\.", File.separator );
        final Host host = catalina.createHost( "localhost", docBase );
        engine.addChild( host );
        new File( docBase ).mkdirs();
        
        final MemcachedBackupSessionManager sessionManager = new MemcachedBackupSessionManager();
        sessionManager.setMemcachedNodes( memcachedNodes );
        sessionManager.setActiveNodeIndex( 0 );
        sessionManager.setMaxInactiveInterval( 1 ); // 1 second
        sessionManager.setProcessExpiresFrequency( 1 ); // 1 second (factor for context.setBackgroundProcessorDelay)

        final Context context = catalina.createContext( "/", "webapp" );
        context.setManager( sessionManager );
        context.setBackgroundProcessorDelay( 1 );
        
        host.addChild( context );
        
        catalina.addEngine( engine );
        
        final Connector connector = catalina.createConnector( InetAddress.getLocalHost(), port, false );
        catalina.addConnector( connector );
        
        return catalina;
    }

}