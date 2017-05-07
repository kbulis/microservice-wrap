package com.unowmo.microwrap;

import java.io.*;
import java.util.*;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Uses hmac-sha256 hashing to generate signatures of content for transmission.
 * 
 * @author Kirk Bulis
 *
 */
public class StringHashSigner implements AutoCloseable {
	private final Mac mac;
	
	/**
	 * Initializes mac with secret key for hashing content for transmission.
	 * 
     * @param secretKey base64 string of secret key for hashing
     * @exception IOException unable to initialize mac
     * @return this string hash signer
	 */
	public StringHashSigner initialize(final String secretKey) throws IOException {
		try
		{
	        this.mac.init
	        	( new SecretKeySpec(Base64.getDecoder().decode(secretKey), StringHashSigner.algorithm)
	        	);
			
			return this;
		}
		catch (InvalidKeyException eX)
		{
			throw new IOException
				( "Unable to initialize mac"
				, eX
				);
		}
	}
	
	/**
	 * Signs content according to initialized mac using passed secret key.
	 * 
	 * @param content payload to hash
	 * @exception IOException failure to sign content
	 * @return base64 representation of hash
	 */
	public String sign(final String content) throws IOException {
		try
		{
			return Base64.getEncoder().encodeToString(this.mac.doFinal(content.getBytes("utf8")));
		}
		catch (IllegalStateException | UnsupportedEncodingException eX)
		{
			throw new IOException
				( "Failed to sign content"
				, eX
				);
		}
	}

	/**
	 * Cleans up allocated resources.
	 */
	@Override
	public void close() {
		this.mac.reset();
	}
	
	/**
	 * Construct default.
	 * 
     * @param secretKey base64 string of secret key for hashing
     * @exception IOException unable to initialize mac
	 */
	public StringHashSigner(final String secretKey) throws IOException {
		try
		{
	        this.mac = Mac.getInstance(StringHashSigner.algorithm);

	        this.mac.init
	        	( new SecretKeySpec(Base64.getDecoder().decode(secretKey), StringHashSigner.algorithm)
	        	);
		}
		catch (NoSuchAlgorithmException | InvalidKeyException eX)
		{
			throw new IOException
				( "Unable to initialize mac"
				, eX
				);
		}
	}
	
	/**
	 * Construct default.
	 * 
     * @exception IOException unable to initialize mac
	 */
	public StringHashSigner() throws IOException {
		try
		{
	        this.mac = Mac.getInstance(StringHashSigner.algorithm);
		}
		catch (NoSuchAlgorithmException eX)
		{
			throw new IOException
				( "Unable to initialize mac"
				, eX
				);
		}
	}

	private static String algorithm = "HMACSHA256";

}
