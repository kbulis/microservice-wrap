package com.unowmo.microwrap;

import java.util.*;
import java.io.*;
import com.fasterxml.jackson.databind.*;

/**
 * Captures signed identity token for web api service endpoints according to the
 * jwt convention of storing authenticated users as strings.
 * 
 * @author Kirk Bulis
 *
 */
public class JsonWebUserToken {
    private String headers = "";
    private String payload = "";
    private String issuing = "";
    private String uniqued = "";
    private String signing = "";
    
    static class Headers {
        public String typ = "";
        public String alg = "";
        
    }

    static class Payload {
        public String uid = "";
        public String iss = "";
        public int iat = 0;
        public int exp = 0;
        
    }

    public String getTokened() {
        if (this.headers.equalsIgnoreCase("") == false && this.payload.equalsIgnoreCase("") == false)
        {
            return this.headers + "." + this.payload + "." + this.signing;
        }
        
        return "";
    }

    public String getIssuing() {
        return this.issuing;
    }

    public String getUniqued() {
        return this.uniqued;
    }

    /**
     * Tests extracted elements against secrets-based signature and expiration.
     * 
     * @param secretKey base64 string of secret key for hashing
     * @param nowInSecs epoch time in secs
     * @param mapper used for reading token payload params
     * @return true if valid, otherwise false
     */
    public boolean testIsValid(final String secretKey, final Long nowInSecs, final ObjectMapper mapper) {
        try (final StringHashSigner signer = new StringHashSigner(secretKey))
        {
            Base64.Decoder decoder = Base64.getDecoder();
            
            if (signer.sign(this.headers + "." + this.payload).replace('+', '-').replace('/', '_').equalsIgnoreCase(this.signing) == true)
            {
                Payload p = mapper.readValue
                    ( decoder.decode(this.payload.replace('-', '+').replace('_', '/').getBytes("utf8"))
                    , Payload.class
                    );
                
                if (p.iat <= nowInSecs && nowInSecs <= p.exp)
                {
                    return true;
                }
            }
        }
        catch (Exception eX)
        {
        }
        
        return false;
    }

    /**
     * Generates new web token for transmission using current unix time with
     * fixed expiration delta.
     * 
     * @param secretKey base64 string of secret key for hashing
     * @param nowInSecs epoch time in secs
     * @param expiresAfter interval duration added to now
     */
    public void allowExtension(final String secretKey, final Long nowInSecs, final Long expiresAfter) {
        Base64.Encoder encoder = Base64.getEncoder();

        try
        {
            this.headers = encoder.encodeToString
                ( "{ ''typ'': ''JWT'', ''alg'': ''HS256'' }".replace("''", "\"").getBytes("utf8")
                ).replace('+', '-').replace('/', '_');

            this.payload = encoder.encodeToString
                ( String.format
                    ( "{ ''uid'': ''%s'', ''iss'': ''%s'', ''iat'': %d, ''exp'': %d }"
                    , this.uniqued
                    , this.issuing
                    , nowInSecs
                    , nowInSecs + expiresAfter
                    ).replace("''", "\"").getBytes("utf8")
                ).replace('+', '-').replace('/', '_');

            try (final StringHashSigner signer = new StringHashSigner(secretKey))
            {
                this.signing = signer.sign(this.headers + "." + this.payload).replace('+', '-').replace('/', '_');
            }
        }
        catch (Exception eX)
        {
        }
    }

    /**
     * Construct default.
     * 
     * @param issuer application-specific label of issuing service
     * @param user application-specific user identity code
     * @param secretKey base64 string of secret key for hashing
     * @param nowInSecs epoch time in secs
     * @param expiresAfter interval duration added to now
     * @exception IOException failure to package token
     */
    public JsonWebUserToken(final String issuer, final String user, final String secretKey, final Long nowInSecs, final Long expiresAfter) throws IOException {
        Base64.Encoder encoder = Base64.getEncoder();
        
        try
        {
            this.headers = encoder.encodeToString
                ( "{ ''typ'': ''JWT'', ''alg'': ''HS256'' }".replace("''", "\"").getBytes("utf8")
                ).replace('+', '-').replace('/', '_');

            this.payload = encoder.encodeToString
                ( String.format
                    ( "{ ''uid'': ''%s'', ''iss'': ''%s'', ''iat'': %d, ''exp'': %d }"
                    , user
                    , issuer
                    , nowInSecs
                    , nowInSecs + expiresAfter
                    ).replace("''", "\"").getBytes("utf8")
                ).replace('+', '-').replace('/', '_');

            try (final StringHashSigner signer = new StringHashSigner(secretKey))
            {
                this.signing = signer.sign(this.headers + "." + this.payload).replace('+', '-').replace('/', '_');
            }
            
            this.issuing = issuer;
            this.uniqued = user;
        }
        catch (Exception eX)
        {
        	throw new IOException
        		( "Unable to construct web token"
        		, eX
        		);
        }
    }

    /**
     * Construct default.
     * 
     * @param token serialized jwt representation to be unpacked
     * @param mapper used for reading token payload params
     * @exception IOException failure to unpack token
     */
    public JsonWebUserToken(final String token, final ObjectMapper mapper) throws IOException {
        final String [] parts = (token != null ? (token.indexOf(' ') > 0 ? token.split(" ")[1] : token) : "").split("\\.");

        if (parts.length == 3)
        {
            Base64.Decoder decoder = Base64.getDecoder();

            if (parts[0].equalsIgnoreCase("") == false)
            {
                Headers h = mapper.readValue
                    ( decoder.decode(parts[0].replace('-', '+').replace('_', '/').getBytes("utf8"))
                    , Headers.class
                    );
                
                if (h.typ.equalsIgnoreCase("jwt") == true && h.alg.equalsIgnoreCase("hs256") == true)
                {
                    Payload p = mapper.readValue
                        ( decoder.decode(parts[1].replace('-', '+').replace('_', '/').getBytes("utf8"))
                        , Payload.class
                        );

                    if (p.uid.equalsIgnoreCase("") == false)
                    {
                        this.headers = parts[0];
                        this.payload = parts[1];
                        this.signing = parts[2];

                        this.uniqued = p.uid;
                    }
                }
            }
        }
    }

}