package com.unowmo.microwrap;

import java.util.*;
import com.amazonaws.*;
import com.amazonaws.client.builder.*;

/**
 * AWS Lambda handler implementation that decodes deserialized requests and
 * invokes the logic block associated with that command. Request content is
 * made available to the called via deserialization by reflection.
 * 
 * This layer tracks initialized AWS service clients as singletons for use
 * during multiple requests processing (yes, we assume the same in-memory
 * instance of this class is reused by the AWS Lambda framework).
 * 
 * @author Kirk Bulis
 *
 */
public abstract class CachedServiceApi<T extends CachedServiceApi.ContainerContext> extends MultiEndpointApi<T> {
    private final static Map<String, Cacher> servicePool = new HashMap<String, Cacher>();

    /**
     * Base container implementation for holding onto request-specific values
     * and facilities as part of normal request handling. Container should
     * define a default constructor. 
     */
    public static class ContainerContext extends MultiEndpointApi.ContainerContext {

        public Cacher cacher = null;

    }

    /**
     * Connects and initializes shared context parameters as part of specialized
     * request processing at different layers. Implementers must call the super
     * form before returning.
     * 
     * @param context container context being updated
     * @param trusted request security token
     * @param region location hint for services
     * @param config execution configuration
     * @param logger logging facility
     */
    protected void fixupRequestContainer(final T context, final String command, final String trusted, final String region, final String config, final Tracer logger) {
        super.fixupRequestContainer(context, command, trusted, region, config, logger);
        
        if (servicePool.containsKey(region) == false)
        {
            servicePool.put(region, context.cacher = new Keeper(region));
        }
        else
        {
            context.cacher = servicePool.get(region);
        }
    }

    /**
     * Interface for processing. 
     */
    protected static interface Cacher {
        
        <B extends AwsSyncClientBuilder<B, ?>> AmazonWebServiceClient access(Class<B> builderType);
        
    }
    
    /**
     * Container for processing. 
     */
    private static class Keeper implements Cacher {
        private final ArrayList<Pair> clients = new ArrayList<Pair>();
        private final String region;

        private static class Pair {
            final AmazonWebServiceClient client;
            final Class<?> builderType;
            
            public Pair(AmazonWebServiceClient client, Class<?> builderType) {
                this.builderType = builderType;
                this.client = client;
            }

        }
        
        @SuppressWarnings("unchecked")
        public <B extends AwsSyncClientBuilder<B, ?>> AmazonWebServiceClient access(Class<B> builderType) {
            for (Pair pair : this.clients)
            {
                if (pair.builderType == builderType)
                {
                    return pair.client;
                }
            }

            try
            {
                if (builderType.getMethod("standard") != null)
                {
                    AwsSyncClientBuilder<B, ?> builder = (AwsSyncClientBuilder<B, ?>) builderType.getMethod("standard").invoke(null);

                    if (builder != null)
                    {
                        AmazonWebServiceClient client = (AmazonWebServiceClient) builder.withRegion(this.region).build();
                        
                        this.clients.add
                            ( new Pair
                                ( client
                                , builderType
                                )
                            );

                        return client;
                    }
                }
            }
            catch (Exception eX)
            {
            }
            
            return null;
        }

        public Keeper(final String region) {
            this.region = region;
        }

    }

    /**
     * Construct default.
     * 
     * @param hooks handler endpoint implementations provided by container
     */
    protected CachedServiceApi(final Handled<T> [] hooks) {
        super(hooks);
    }

}