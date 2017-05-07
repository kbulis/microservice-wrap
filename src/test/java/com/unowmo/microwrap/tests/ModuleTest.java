package com.unowmo.microwrap.tests;

import java.util.*;
import java.io.*;
import org.junit.*;
import com.fasterxml.jackson.databind.*;
import com.amazonaws.services.lambda.runtime.*;
import com.unowmo.microwrap.*;

/**
 * A simple test harness for locally invoking your module.
 */
public class ModuleTest {

    private static class MockedApiService extends MultiEndpointApi<MockedApiService.HandleApiContext, MockedApiService.HandleApiWrapper, MockedApiService.HandleApiWrapped> {
        
        private static abstract class Handler extends MultiEndpointApi.Handled<HandleApiContext, HandleApiWrapped> {

            public Handler(String commandLabel) {
                super(commandLabel);
            }

        }

        private static class HandleApiContext extends MultiEndpointApi.ContainerContext {
            
        }
        
        private static class HandleApiWrapped extends MultiEndpointApi.WrappedResources<MockedApiService.HandleApiContext> {
            
			@Override
			public void onCommit(final HandleApiContext context, final Returns returns, Date started) {
			}

        }

		private static class HandleApiWrapper extends MultiEndpointApi.ResourceWrapping<MockedApiService.HandleApiContext> {

			@Override
			public void onCommit(final HandleApiContext context, final String command, final String trusted, Date started) {
			}

			@Override
			public void close() throws Exception {
			}

        }

        @Override
        protected HandleApiContext allocateResourceContext(final String region, final String config) throws IOException {
            return new HandleApiContext();
        }

		@Override
		protected HandleApiWrapper allocateResourceWrapper(final HandleApiContext containerContext) throws IOException {
			return new HandleApiWrapper();
		}
        
		@Override
		protected HandleApiWrapped allocateWrappedResource(final HandleApiContext containerContext, final HandleApiWrapper resourceWrapper) throws IOException {
			return new HandleApiWrapped();
		}

		public MockedApiService() {
            super(new Handler [0]);
        }

    }
    
    @Test
    public void testModule() {
        final MockedApiService handler = new MockedApiService();
        final MockedApiContext context = new MockedApiContext();

    	context.setInvokedFunctionArn("arn:aws:lambda:us-west-2:0:function:microservice-wrap:test");

    	System.out.println("Testing...");

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
            
            try (Response r = mapper.readValue(buffer.toString(), Response.class))
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
    	private String invokedFunctionArn = "";
    	
        private LambdaLogger logger = new LambdaLogger() {
            @Override
            public void log(String message) {
                System.err.println(message);
            }
        };
        
        @Override
        public String getAwsRequestId() {
            return "";
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public String getFunctionName() {
            return "";
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public String getLogGroupName() {
            return "";
        }

        @Override
        public String getLogStreamName() {
            return "";
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
            return 0;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 0;
        }

        @Override
        public String getInvokedFunctionArn() {
            return this.invokedFunctionArn;
        }

        public void setInvokedFunctionArn(final String value) {
        	this.invokedFunctionArn = value;
        }

        @Override
        public String getFunctionVersion() {
            return null;
        }

    }

    static class Response implements AutoCloseable {
        public String results = "";
        public String trusted = "";
        public JsonNode o = null;

        public void close() {
        }
        
    }

    /**
     * Shared facility.
     */
    public static ObjectMapper mapper = new ObjectMapper();
    static {
		mapper.configure
			( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
			, false
			);

		mapper.configure
			( SerializationFeature.FAIL_ON_EMPTY_BEANS
			, false
			);
    };

}
