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
	private final Handled<T> [] hooks; 

	/**
	 * Base container implementation for holding onto request-specific values
	 * and facilities as part of normal request handling. Container should
	 * define a default constructor. 
	 */
	public static class ContainerContext {

		public Tracer logger = null;
		public String detail = null;

	}

	/**
	 * Simple logger wrapper for tracing activity during request processing.
	 */
	public static class Tracer {
		private final LambdaLogger delegate;

		public Tracer log(final String message) {
			if (this.delegate != null)
			{
				this.delegate.log("(" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) + ") " + (message != null ? message : "null"));
			}

			return this;
		}
	
		private Tracer(final LambdaLogger delegate) {
			this.delegate = delegate;
		}

	}
	
	/**
	 * Event for container to initialize context details using given environment
	 * parameters and facilities.
	 * 
	 * @param command request command to process
	 * @param trusted request security token
	 * @param region location hint for services
	 * @param config execution configuration
	 * @param logger logging facility
	 * @return initialized container context instance
	 * @throws IOException raised on any error
	 */
	protected abstract T prepareRequestContainer(final String command, final String trusted, final String region, final String config, final Tracer logger) throws IOException;

	/**
	 * Connects and initializes shared context parameters as part of specialized
	 * request processing at different layers. Implementers must call the super
	 * form before returning.
	 * 
	 * @param context container context being updated
	 * @param command request command to process
	 * @param trusted request security token
	 * @param region location hint for services
	 * @param config execution configuration
	 * @param logger logging facility
	 */
	protected void fixupRequestContainer(final T context, final String command, final String trusted, final String region, final String config, final Tracer logger) {
		context.detail = String.format
			( "Processing '%s' with token '%s' in " + region + " as " + config
			, command
			, trusted
			);
		
		context.logger = logger;
	}
	
	/**
	 * Actual custom lambda handler hook.
	 * 
	 * @param source request body streamed
	 * @param target response body output
	 * @param context execution context
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
        		Returns answer;

				answer = new Returns
					( String.format
						( "command request '%s' not supported"
						, deserialized.command
						)
					);
        		
        		if (deserialized.command.equalsIgnoreCase("getappdetail") == false)
        		{
        			Tracer logger = new Tracer(context != null ? context.getLogger() : null);

        			try
        			{
        				final T containerContext = this.prepareRequestContainer(deserialized.command, deserialized.trusted, region, config, logger);

	            		this.fixupRequestContainer
	            			( containerContext
	            			, deserialized.command
	            			, deserialized.trusted
	            			, region
	            			, config
	            			, logger
	            			);

	            		containerContext.logger.log
	    	    			( "running '" + deserialized.command + "' with request = " + deserialized.request
	    	    			);
	            		
	            		for (final Handled<T> handler : this.hooks)
	            		{
	            			if (handler != null && handler.command.equalsIgnoreCase(deserialized.command) == true)
	            			{
	        					Object object = handler.doCommand(containerContext, deserialized.request.toString(), started);
	            				
	                    		if (object != null)
	                    		{
	                    			containerContext.logger.log
	    	        	    			( "success"
	    	        	    			);

	                    			answer = new Returns
	                    				( "Success"
	                    				, object
	                    				);
	                    		}
	                    		else
	                    		{
	                    			throw new IOException
	                    				( "Handler logic response came back null"
	                    				);
	                    		}
	            			}
	            		}
        			}
        			catch (IOException eX)
        			{
        				throw eX;
        			}
        			catch (Exception eX)
        			{
        				throw new IOException
        					( "Unable to initialize container and execute command"
        					, eX
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
	                        
		        			answer = new Returns
		        				( "Success"
		        				, new Version
		        					( properties.getProperty("detail.name")
		        					, properties.getProperty("detail.version")
		        					)
		        				);
        				}
        				else
        				{
        					answer = new Returns
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
        				answer = new Returns
        					( String.format
        						( "failed%s"
        						, eX.getMessage() != null ? " because " + eX.getMessage().toLowerCase() : ""
        						)
        					);
        			}
        		}
        		
        		responseOf = answer != null ? mapper.toJson(answer) : "{ }";
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
	public static abstract class Handled<T extends ContainerContext> {
		final String command;
		
		public abstract Object doCommand(final T context, final String posting, final Date started) throws IOException;

		public Handled(final String commandLabel) {
			this.command = commandLabel;
		}

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
	 * 
	 * @param hooks handler endpoint implementations provided by container
	 */
	protected MultiEndpointApi(final Handled<T> [] hooks) {
		this.hooks = hooks;
	}

	/**
	 * Shared facility.
	 */
	protected static Gson mapper = new Gson();

}