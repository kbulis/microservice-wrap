package com.unowmo.microwrap.tests;

import java.util.*;
import java.io.*;
import org.junit.*;
import com.google.gson.*;
import com.amazonaws.services.lambda.runtime.*;
import com.unowmo.microwrap.*;

/**
 * A simple test harness for locally invoking your module.
 */
public class ModuleTest {

    private static class MockedApiService extends MultiEndpointApi<MockedApiService.ServiceContext> {
        
        private static abstract class Handler extends MultiEndpointApi.Handled<ServiceContext> {

            public Handler(String commandLabel) {
                super(commandLabel);
            }

        }

        private static class ServiceContext extends MultiEndpointApi.ContainerContext {
            
        }
        
        @Override
        public ServiceContext prepareRequestContainer(final String command, final String trusted, final String region, final String config, final Tracer logger) throws IOException {
            return new ServiceContext();
        }

        public MockedApiService() {
            super(new Handler [0]);
        }

    }
    
    @Test
    public void testModule() {
        final MockedApiService handler = new MockedApiService();
        final MockedApiContext context = new MockedApiContext();

        System.out.println("Testing...");

        context.getClientContext().getCustom().put("region", "us-west-2");
        context.getClientContext().getCustom().put("config", "test");
        
        try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream())
        {
            handler.handleRequest
                ( new ByteArrayInputStream
                    ( String.format
                        ( "{ ''command'': ''getappdetail''"
                        + ", ''request'':"
                        + " { ''o'':"
                        + " { ''options'': ''testing''"
                        + " }"
                        + " }"
                        + ", ''trusted'': ''''"
                        + " }"
                        ).replace("''",  "\"").getBytes("utf8")
                    )
                , buffer
                , context
                );
            
            try (Response r = mapper.fromJson(buffer.toString(), Response.class))
            {
                Assert.assertTrue
                    ( "Failed to access app details"
                    , r.results.equalsIgnoreCase("success") == true
                    );
            }
            finally
            {
                System.out.println
                    ( buffer.toString()
                     );
                
                buffer.reset();
            }
        }
        catch (AssertionError eX)
        {
            throw eX;
        }
        catch (Exception eX)
        {
            Assert.fail
                ( "Oops because " + eX.getMessage().toLowerCase()
                );
        }
        finally
        {
            System.out.println
                ( "Done."
                );
        }
    }

    static class MockedApiContext implements Context {
        private String awsRequestId = "test";
        private String functionName = "test";
        private String logGroupName = "test";
        private String logStreamName = "test";
        private int memoryLimitInMB = 128;
        private int remainingTimeInMillis = 15000;

        private ClientContext clientContext = new ClientContext() {
            private Map<String, String> custom = new HashMap<String, String>();
            @Override
            public Map<String, String> getEnvironment() {
                return this.custom;
            }
            @Override
            public Map<String, String> getCustom() {
                return this.custom;
            }
            @Override
            public Client getClient() {
                return null;
            }
        };

        private CognitoIdentity identity = new CognitoIdentity() {
            @Override
            public String getIdentityId() {
                return "";
            }
            @Override
            public String getIdentityPoolId() {
                return "";
            }
        };
        
        private LambdaLogger logger = new LambdaLogger() {
            @Override
            public void log(String message) {
                System.err.println(message);
            }
        };
        
        @Override
        public String getAwsRequestId() {
            return awsRequestId;
        }

        public void setAwsRequestId(String value) {
            awsRequestId = value;
        }

        @Override
        public ClientContext getClientContext() {
            return clientContext;
        }

        public void setClientContext(ClientContext value) {
            clientContext = value;
        }

        @Override
        public String getFunctionName() {
            return functionName;
        }

        public void setFunctionName(String value) {
            functionName = value;
        }

        @Override
        public CognitoIdentity getIdentity() {
            return identity;
        }

        public void setIdentity(CognitoIdentity value) {
            identity = value;
        }

        @Override
        public String getLogGroupName() {
            return logGroupName;
        }

        public void setLogGroupName(String value) {
            logGroupName = value;
        }

        @Override
        public String getLogStreamName() {
            return logStreamName;
        }

        public void setLogStreamName(String value) {
            logStreamName = value;
        }

        @Override
        public LambdaLogger getLogger() {
            return logger;
        }

        public void setLogger(LambdaLogger value) {
            logger = value;
        }

        @Override
        public int getMemoryLimitInMB() {
            return memoryLimitInMB;
        }

        public void setMemoryLimitInMB(int value) {
            memoryLimitInMB = value;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return remainingTimeInMillis;
        }

        public void setRemainingTimeInMillis(int value) {
            remainingTimeInMillis = value;
        }

        @Override
        public String getInvokedFunctionArn() {
            return null;
        }

        @Override
        public String getFunctionVersion() {
            return null;
        }

    }

    static class Response implements AutoCloseable {
        public String results = "";
        public String trusted = "";
        public JsonObject o = null;

        public void close() {
        }

    }
    
    private static Gson mapper = new Gson();

}
