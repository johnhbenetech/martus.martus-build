package org.martus.meta;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.martus.client.core.BulletinStore;
import org.martus.common.AttachmentProxy;
import org.martus.common.Bulletin;
import org.martus.common.BulletinHeaderPacket;
import org.martus.common.ByteArrayInputStreamWithSeek;
import org.martus.common.Database;
import org.martus.common.DatabaseKey;
import org.martus.common.InputStreamWithSeek;
import org.martus.common.MartusSecurity;
import org.martus.common.MartusUtilities;
import org.martus.common.MockClientDatabase;
import org.martus.common.TestCaseEnhanced;
import org.martus.common.MartusCrypto.DecryptionException;
import org.martus.common.MartusUtilities.FileVerificationException;
import org.martus.common.Packet.InvalidPacketException;
import org.martus.common.Packet.SignatureVerificationException;
import org.martus.common.Packet.WrongAccountException;

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
	
	public void testValidateIntegrityOfZipFilePackets() throws Exception
	{
		Database db = new MockClientDatabase();
		BulletinStore store = new BulletinStore(db);
		store.setSignatureGenerator(security);

		File sampleAttachment = createTempFile("This is some data");
		AttachmentProxy ap = new AttachmentProxy(sampleAttachment);

		Bulletin b = store.createEmptyBulletin();
		b.addPublicAttachment(ap);
		store.saveBulletin(b);
		String accountId = b.getAccount();
		DatabaseKey key = DatabaseKey.createKey(b.getUniversalId(), b.getStatus());

		File originalZipFile = createTempFile();
		MartusUtilities.exportBulletinPacketsFromDatabaseToZipFile(db, key, originalZipFile, security);
		validateZipFile(accountId, originalZipFile);

		File copiedZipFile = createCopyOfZipFile(originalZipFile, null, null);
		validateZipFile(accountId, copiedZipFile);
		
		File zipWithoutHeaderPacket = createCopyOfZipFile(originalZipFile, "B-", null);
		try
		{
			validateZipFile(accountId, zipWithoutHeaderPacket);
			fail("Should have thrown for missing header");
		}
		catch (IOException ignoreExpectedException)
		{
		}

		File zipWithoutDataPackets = createCopyOfZipFile(originalZipFile, "F-", null);
		try
		{
			validateZipFile(accountId, zipWithoutDataPackets);
			fail("Should have thrown for missing data packets");
		}
		catch (IOException ignoreExpectedException)
		{
		}

		File zipWithoutAttachmentPackets = createCopyOfZipFile(originalZipFile, "A-", null);
		try
		{
			validateZipFile(accountId, zipWithoutAttachmentPackets);
			fail("Should have thrown for missing attachment");
		}
		catch (IOException ignoreExpectedException)
		{
		}
		
		File zipWithExtraEntry = createCopyOfZipFile(originalZipFile, null, "unexpected");
		try
		{
			validateZipFile(accountId, zipWithExtraEntry);
			fail("Should have thrown for extra entry");
		}
		catch (IOException ignoreExpectedException)
		{
		}
		
		File zipWithRelativePathInformation = createCopyOfZipFile(originalZipFile, null, "../../../acctmap.txt");
		try
		{
			validateZipFile(accountId, zipWithRelativePathInformation);
			fail("Should have thrown for relative path in name");
		}
		catch(InvalidPacketException ignoreExpectedException)
		{
		}
		
		File zipWithAbsolutePathInformation = createCopyOfZipFile(originalZipFile, null, "c:/MartusServer/packets/acctmap.txt");
		try
		{
			validateZipFile(accountId, zipWithAbsolutePathInformation);
			fail("Should have thrown for absolute path in name");
		}
		catch(InvalidPacketException ignoreExpectedException)
		{
		}
	}

	private void validateZipFile(String accountId, File copiedZipFile)
		throws
			ZipException,
			IOException,
			InvalidPacketException,
			SignatureVerificationException,
			WrongAccountException,
			DecryptionException
	{
		ZipFile copiedZip = new ZipFile(copiedZipFile);
		MartusUtilities.validateIntegrityOfZipFilePackets(accountId, copiedZip, security);
		copiedZip.close();
	}

	private File createCopyOfZipFile(File tempZipFile, String excludeStartsWith, String entryToAdd)
		throws IOException, FileNotFoundException, ZipException
	{
		File copiedZipFile = createTempFile();
		ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(copiedZipFile));
		
		ZipFile zip = new ZipFile(tempZipFile);
		Enumeration entries = zip.entries();
		while(entries.hasMoreElements())
		{
			ZipEntry entry = (ZipEntry)entries.nextElement();
			if(excludeStartsWith != null && entry.getName().startsWith(excludeStartsWith))
				continue;
			InputStream in = new BufferedInputStream(zip.getInputStream(entry));
			zipOut.putNextEntry(entry);
			int dataLength = (int)entry.getSize();
			byte[] data = new byte[dataLength];
			in.read(data);
			zipOut.write(data);
		}
		if(entryToAdd != null)
		{
			ZipEntry newEntry = new ZipEntry(entryToAdd);
			zipOut.putNextEntry(newEntry);
		}
		zip.close();
		zipOut.close();
		return copiedZipFile;
	}
	
	public void testGetBulletinSize() throws Exception
	{
		byte[] b1AttachmentBytes = {1,2,3,4,4,3,2,1};
		MartusSecurity security = new MartusSecurity();
		security.createKeyPair(512);
		BulletinStore store = new BulletinStore(new MockClientDatabase());
		store.setSignatureGenerator(security);
		Database db = store.getDatabase();

		Bulletin b1 = store.createEmptyBulletin();
		store.saveBulletin(b1);
		BulletinHeaderPacket bhp = b1.getBulletinHeaderPacket();
		int emptySize = MartusUtilities.getBulletinSize(db, bhp);
		assertTrue("empty size not correct?", emptySize > 1000 && emptySize < 3000);
		b1.set(Bulletin.TAGTITLE, "Title");
		b1.set(Bulletin.TAGPUBLICINFO, "Details1");
		b1.set(Bulletin.TAGPRIVATEINFO, "PrivateDetails1");
		File attachment = createTempFile();
		FileOutputStream out = new FileOutputStream(attachment);
		out.write(b1AttachmentBytes);
		out.close();
		b1.addPublicAttachment(new AttachmentProxy(attachment));
		b1.addPrivateAttachment(new AttachmentProxy(attachment));
		store.saveBulletin(b1);
		b1 = store.loadFromDatabase(DatabaseKey.createSealedKey(b1.getUniversalId()));

		int size = MartusUtilities.getBulletinSize(db, bhp);
		b1.set(Bulletin.TAGTITLE, "This is an very long title and should change the size of the result if things are working correctly");
		store.saveBulletin(b1);
		int size2 = MartusUtilities.getBulletinSize(db, bhp);
		assertTrue("Size too small?", size > 4000);
		assertNotEquals("Sizes match?", size, size2);		
	}
	
	public void testCreateSignatureFromFile()
		throws Exception
	{
		MartusSecurity otherSecurity = new MartusSecurity();
		otherSecurity.createKeyPair(512);
		
		String string1 = "The string to write into the file to sign.";
		String string2 = "The other string to write to another file to sign.";
		
		File normalFile = createTempFile(string1);
		File anotherFile = createTempFile(string2);

		File normalFileSigBySecurity = MartusUtilities.createSignatureFileFromFile(normalFile, security);

		MartusUtilities.verifyFileAndSignature(normalFile, normalFileSigBySecurity, security, security.getPublicKeyString());
		
		try
		{
			MartusUtilities.verifyFileAndSignature(normalFile, normalFileSigBySecurity, security, "this would be a different public key");
			fail("signature file's public key is not the verifiers public key should have thrown.");
		}
		catch (FileVerificationException ignoreExpectedException)
		{
		}

		try
		{
			MartusUtilities.verifyFileAndSignature(anotherFile, normalFileSigBySecurity, security, security.getPublicKeyString());
			fail("testCreateSignatureFromFile 1: Should have thrown FileVerificationException.");
		}
		catch (FileVerificationException ignoreExpectedException)
		{
		}

		normalFileSigBySecurity.delete();
		normalFile.delete();
		anotherFile.delete();
		
		try
		{
			MartusUtilities.verifyFileAndSignature(anotherFile, normalFileSigBySecurity, security, security.getPublicKeyString());
			fail("testCreateSignatureFromFile 2: Should have thrown FileVerificationException.");
		}
		catch (FileVerificationException ignoreExpectedException)
		{
		}
	}
	
	public void testDoesPacketNeedLocalEncryption() throws Exception
	{
		final String accountId = "dummy";
		BulletinHeaderPacket bhpWithoutFlag = new BulletinHeaderPacket(accountId);
		BulletinHeaderPacket bhpWithFlagPrivate = new BulletinHeaderPacket(accountId);
		bhpWithFlagPrivate.setAllPrivate(true);
		BulletinHeaderPacket bhpWithFlagPublic = new BulletinHeaderPacket(accountId);
		bhpWithFlagPublic.setAllPrivate(false);
		byte[] binaryEncryptedData = {0, 1, 2, 3};
		byte[] tagEncryptedData = new String("blah blah blah\n<Encrypted> blah blah").getBytes();
		byte[] plainTextData = new String("There is nothing here\nto indicate that it is Encrypted!").getBytes();

		verifyDoesPacketNeedLocalEncryption("headerSaysPublic, binary encrypted", 
						false, bhpWithFlagPublic, binaryEncryptedData);
		verifyDoesPacketNeedLocalEncryption("headerSaysPublic, tag encrypted", 
						false, bhpWithFlagPublic, tagEncryptedData);
		verifyDoesPacketNeedLocalEncryption("headerSaysPublic, plain text", 
						true, bhpWithFlagPublic, plainTextData);
			
		verifyDoesPacketNeedLocalEncryption("headerDoesntKnow, binary encrypted", 
						false, bhpWithoutFlag, binaryEncryptedData);
		verifyDoesPacketNeedLocalEncryption("headerDoesntKnow, tag encrypted", 
						false, bhpWithoutFlag, tagEncryptedData);
		verifyDoesPacketNeedLocalEncryption("headerDoesntKnow, plain text", 
						true, bhpWithoutFlag, plainTextData);

		verifyDoesPacketNeedLocalEncryption("headerSaysPrivate, binary encrypted", 
						false, bhpWithFlagPrivate, binaryEncryptedData);
		verifyDoesPacketNeedLocalEncryption("headerSaysPrivate, tag encrypted", 
						false, bhpWithFlagPrivate, tagEncryptedData);
		verifyDoesPacketNeedLocalEncryption("headerSaysPrivate, plain text", 
						false, bhpWithFlagPrivate, plainTextData);
	}

	public void verifyDoesPacketNeedLocalEncryption(String label, boolean expected,
							BulletinHeaderPacket bhp, byte[] bytes1)
		throws IOException
	{
		InputStreamWithSeek in = new ByteArrayInputStreamWithSeek(bytes1);
		int firstByte = in.read();
		in.seek(0);
		assertEquals(label, expected, 
				MartusUtilities.doesPacketNeedLocalEncryption(bhp, in));
		assertEquals(label + " didn't reset?", firstByte, in.read());
	}
	
	static MartusSecurity security;
}
