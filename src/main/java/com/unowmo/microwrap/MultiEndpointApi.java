package com.unowmo.microwrap;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import com.google.gson.*;
import com.amazonaws.services.lambda.runtime.*;

/**
 * AWS Lambda handler implementation that decodes deserialized requests and
 * invokes the logic block associated with that command. Request content is
 * made available to the called via deserialization by reflection.
 * 
 * @author Kirk Bulis
 *
 */
public abstract class MultiEndpointApi<T extends MultiEndpointApi.ContainerContext> implements RequestStreamHandler {
	private final Handler<T> [] hooks; 

	/**
	 * Base container implementation for holding onto request-specific values
	 * and facilities as part of normal request handling. Container should
	 * define a default constructor. 
	 */
	public static class ContainerContext {

	}

	/**
	 * Simple logger wrapper for tracing activity during request processing.
	 */
	public static class Logging {
		private final LambdaLogger delegate;

		public Logging log(final String message) {
			if (this.delegate != null)
			{
				this.delegate.log("(" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + ") " + (message != null ? message : "null"));
			}

			return this;
		}
	
		private Logging(final LambdaLogger delegate) {
			this.delegate = delegate;
		}

	}
	
	/**
	 * ...
	 * 
	 * @param context
	 * @param region
	 * @param config
	 */
	public abstract T prepareRequestContainer(final String command, final String region, final String config, final Logging logger) throws IOException;
	
	/**
	 * Actual custom lambda handler hook.
	 * 
	 * @param source
	 * @param target
	 * @param context
	 */
	@Override
	public final void handleRequest(final InputStream source, final OutputStream target, final Context context) {
		final Date started = new Date();

        try
        {
        	// Looks up region and config specified through calling context
        	// provided by execution container. Though not platform specific,
        	// the use of both was born from aws convention.

    		String config = "";
    		String region = "";

        	if (context != null)
        	{
        		if (context.getInvokedFunctionArn() != null)
        		{
	        		String [] arn = context.getInvokedFunctionArn().split(":");
	        		
	        		if (arn.length > 3)
	        		{
	        			region = arn[3].trim();
	        			
	        			config = "prod";
	        		}
        		}
        		
        		if (context.getClientContext() != null)
        		{
        			region = context.getClientContext().getCustom().getOrDefault
        				( "region"
        				, region
        				);

        			config = context.getClientContext().getCustom().getOrDefault
        				( "config"
        				, config
        				);
        		}
        	}

        	if (region.equalsIgnoreCase("") == true)
        	{
        		throw new IOException
	    			( "Handler request context lacks valid region"
	    			);
        	}
        	
        	if (config.equalsIgnoreCase("") == true)
        	{
        		throw new IOException
	    			( "Handler request context lacks valid config"
	    			);
        	}

        	// Execute actual implementation and serialize result for calling
        	// container to consume. Expects json, which is another convention
        	// related to aws implementation, but general enough to prove
        	// useful in a wide range of platforms.
        	//
        	// We look for matching request command handlers in the order
        	// given on construction. This can be optimized, but we don't
        	// really expect a large set.

        	String requesting = "";
        	String responseOf = "";

        	if (source == null)
        	{
        		throw new IOException
        			( "Invalid command request source; expecting body with command parameter"
        			);
        	}
        	
        	try (final InputStreamReader reader = new InputStreamReader(source, "utf-8"))
        	{
        		final char buffer[] = new char[16 * 1024];

        		while (true)
        		{
        			int n = reader.read(buffer);
        			
        			if (n > 0)
        			{
        				requesting += new String(buffer, 0, n);
        			}
        			else
        			if (n < 0)
        			{
        				break;
        			}
        		}
        	}
        	
        	try
        	{
        		Posting deserialized = mapper.fromJson(requesting, Posting.class);
        		Returns object;
        		Logging logger;
        		
        		if (context != null)
        		{
        			logger = new Logging(context.getLogger());
        		}
        		else
        		{
        			logger = new Logging(null);
        		}

        		object = null;
        		
        		if (deserialized.command.equalsIgnoreCase("getappdetail") == false)
        		{
        			final T containerContext = this.prepareRequestContainer(deserialized.command, region, config, logger);
        			boolean matching = false;

            		logger.log
    	    			( "running '" + deserialized.command + "' with request = " + deserialized.request
    	    			);

            		for (final Handler<T> handler : this.hooks)
            		{
            			if (handler != null && handler.getCommandLabel().equalsIgnoreCase(deserialized.command) == true)
            			{
            				matching = true;

        					object = handler.doSomething(containerContext, requesting, started);
            				
                    		if (object != null)
                    		{
        	    	        	if (object.results.equalsIgnoreCase("success") == false)
        	    	        	{
        	                		logger.log
        	        	    			( "failure '" + object.results + "'"
        	        	    			);
        	    	        	}
        	    	        	else
        	    	        	{
        	                		logger.log
        	        	    			( "success"
        	        	    			);
        	    	        	}
                    		}
                    		else
                    		{
                    			throw new IOException
                    				( "Handler logic response came back null"
                    				);
                    		}
            			}
            		}
            		
            		if (matching == false)
            		{
        				object = new Returns
        					( String.format
        						( "command request '%s' not supported"
        						, deserialized.command
        						)
        					);
            		}
        		}
        		else
        		{
        			try (final InputStream declared = this.getClass().getClassLoader().getResourceAsStream("app.properties"))
        			{
        				if (declared != null)
        				{
	        				Properties properties = new Properties();
	
	                    	properties.load
	                    		( declared 
	                    		);
	                        
		        			object = new Returns
		        				( "Success"
		        				, new Version
		        					( properties.getProperty("detail.name")
		        					, properties.getProperty("detail.version")
		        					)
		        				);
        				}
        				else
        				{
        					object = new Returns
		        				( "Success"
		        				, new Version
		        					( "unknown"
		        					, "0.0.0"
		        					)
		        				);
        				}
        			}
        			catch (Exception eX)
        			{
        				object = new Returns
        					( String.format
        						( "failed%s"
        						, eX.getMessage() != null ? " because " + eX.getMessage().toLowerCase() : ""
        						)
        					);
        			}
        		}
        		
        		responseOf = object != null ? mapper.toJson(object) : "{ }";
        	}
        	catch (Exception eX)
        	{
        		throw eX;
        	}

        	// Now writing whatever result was obtained as a serialized blob
        	// with replacements for operational parameters if the container
        	// cares to inject them.
        	//
        	// Note that a failure to write could throw out and subsequently
        	// append the exception details.
        	
        	try
        	{
        		final Date wrapped = new Date();

        		responseOf = responseOf.replace("((running))", "" + (wrapped.getTime() - started.getTime()));
        		responseOf = responseOf.replace("((execute))", "" + (context.getAwsRequestId()));
        		responseOf = responseOf.replace("((started))", "" + (started.getTime()));
        		responseOf = responseOf.replace("((wrapped))", "" + (wrapped.getTime()));
        		
		        target.write
		        	( responseOf.replace("''",  "\"").getBytes("utf8")
		        	);
        	}
        	catch (Exception eX)
        	{
        		throw eX;
        	}

        	return;
        }
		catch (Exception eX)
		{
			// Oops. Handle error and let's figure this out.
	
	        try
	        {
		        target.write
		        	( String.format
		        		( "{ ''results'': ''Failed%s'', ''started'': %d, ''execute'': ''%s'' }"
		        		, eX.getMessage() != null ? " because " + eX.getMessage().toLowerCase().replace('\'', '`') : ""
		        		, started.getTime()
		        		, context.getAwsRequestId()
		        		).replace("''",  "\"").getBytes("utf8")
		        	);
	        }
	        catch (Exception nX)
	        {
	        }

	        if (context != null)
	        {
		        context.getLogger().log
		        	( String.format
		        		( "failed%s"
		        		, eX.getMessage() != null ? " because " + eX.getMessage().toLowerCase() : ""
		        		)
		        	);
	        }
		}
	}
	
	/**
	 * Base interface for all handler hooks to process requests. 
	 */
	public static interface Handler<T extends ContainerContext> {

		Returns doSomething(final T context, final String request, final Date started);

		String getCommandLabel();

	}

	/**
	 * Container for processing. 
	 */
	public static class Posting {

		public String command = "";
		public String trusted = "";
		public JsonObject request = null;

	}

	/**
	 * Container for processing. 
	 */
	public static class Returns {

		public String results = "";
		public Object o = null;
		
		public Returns(final String results, final Object o) {
			this.results = results != null ? results.replace('"', '\'') : "";
			this.o = o;
		}

		public Returns(final String results) {
			this.results = results != null ? results.replace('"', '\'') : "";
		}

		final String running = "((running))";
		final String execute = "((execute))";
		
	}

	/**
	 * Container for processing. 
	 */
	public static class Version {

		public final String name;
		public final String version;

		public Version(final String name, final String version) {
			this.name = name;
			this.version = version;
		}

	}
	
	/**
	 * Construct default.
	 */
	public MultiEndpointApi(final Handler<T> [] hooks) {
		this.hooks = hooks;
	}

	/**
	 * Shared facility.
	 */
	private static Gson mapper = new Gson();

}