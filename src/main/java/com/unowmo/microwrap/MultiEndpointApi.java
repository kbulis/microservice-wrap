package com.unowmo.microwrap;

import java.util.*;
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
public class MultiEndpointApi implements RequestStreamHandler {

	/**
	 * Actual custom lambda handler hook.
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

        		if (deserialized.command.equalsIgnoreCase("getappdetail") == false)
        		{
            		context.getLogger().log
    	    			( "running '" + deserialized.command + "' with request = " + deserialized.request
    	    			);
        			
    	        	//object = this.doSomething(deserialized, context, caching, binding, config, region, vector, secret, started);
            		object = null;

            		if (object == null)
            		{
            			object = new Returns("Null");
            		}
            		
    	        	if (object.results.equalsIgnoreCase("success") == false)
    	        	{
                		context.getLogger().log
        	    			( "failure '" + object.results + "'"
        	    			);
    	        	}
    	        	else
    	        	{
                		context.getLogger().log
        	    			( "success"
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
        					( "Failed" + eX.getMessage() != null ? " because " + eX.getMessage().toLowerCase() : ""
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

	        context.getLogger().log
	        	( String.format
	        		( "Failed%s"
	        		, eX.getMessage() != null ? " because " + eX.getMessage().toLowerCase() : ""
	        		)
	        	);
		}
	}

	/**
	 * Container for processing. 
	 */
	static class Posting {

		String command = "";
		JsonObject request = null;

	}

	/**
	 * Container for processing. 
	 */
	static class Returns {

		String results = "";
		Object o = null;
		
		Returns(final String results, final Object o) {
			this.results = results != null ? results.replace('"', '\'') : "";
			this.o = o;
		}

		Returns(final String results) {
			this.results = results != null ? results.replace('"', '\'') : "";
		}

		final String running = "((running))";
		final String execute = "((execute))";
		
	}

	/**
	 * Container for processing. 
	 */
	static class Version {

		final String name;
		final String version;

		Version(final String name, final String version) {
			this.name = name;
			this.version = version;
		}

	}
	
	/**
	 * Construct default.
	 */
	public MultiEndpointApi() {
	}

	/**
	 * Shared facility.
	 */
	public static Gson mapper = new Gson();
	
}