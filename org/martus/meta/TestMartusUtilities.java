package org.martus.meta;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.SyncFailedException;

import org.martus.common.MartusSecurity;
import org.martus.common.TestCaseEnhanced;
import org.martus.common.UnicodeWriter;
import org.martus.common.MartusCrypto.MartusSignatureException;
import org.martus.common.MartusCrypto.CryptoInitializationException;
import org.martus.common.MartusUtilities.FileSigningException;
import org.martus.common.MartusUtilities.FileVerificationException;

import org.martus.common.MartusUtilities;

public class TestMartusUtilities extends TestCaseEnhanced 
{
	public TestMartusUtilities(String name) 
	{
		super(name);
	}
	
	public void setUp() throws Exception
    {
    	if(security == null)
		{
			security = new MartusSecurity();
			security.createKeyPair(512);
		}
    }

	// TODO: create tests for all the MartusUtilities methods
	public void testBasics()
	{
	}
	
	public void testCreateSignatureFromFile()
		throws IOException, CryptoInitializationException, MartusSignatureException, FileSigningException
	{
		MartusSecurity bogusSecurity = new MartusSecurity();
		bogusSecurity.createKeyPair(512);
		
		String string1 = "The string to write into the file to sign.";
		String string2 = "The other string to write to another file to sign.";
		
		File tempFile = createTempFile(string1);
		File bogusTempFile = createTempFile(string2);

		File sigFile = MartusUtilities.createSignatureFromFile(tempFile, security);
		
		try
		{
			MartusUtilities.verifyFileAndSignature(tempFile, sigFile, security );
		}
		catch (FileVerificationException e)
		{
			fail("testCreateSignatureFromFile: Should not have thrown this exception");
		}
		
		try
		{
			MartusUtilities.verifyFileAndSignature(bogusTempFile, sigFile, security );
			fail("testCreateSignatureFromFile: Should have thrown FileVerificationException.");
		}
		catch (FileVerificationException ignoreExpectedException)
		{
			;
		}
		
		bogusTempFile = createTempFile(string1);
		File bogusSigFile = MartusUtilities.createSignatureFromFile(bogusTempFile, security);
		try
		{
			MartusUtilities.verifyFileAndSignature(bogusTempFile, sigFile, security );
		}
		catch (FileVerificationException e)
		{
			fail("testCreateSignatureFromFile2: Should not have thrown this exception");
		}
	}
	
	static MartusSecurity security;
}
