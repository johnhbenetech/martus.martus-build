/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2003, Beneficent
Technology, Inc. (Benetech).

Martus is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either
version 2 of the License, or (at your option) any later
version with the additions and exceptions described in the
accompanying Martus license file entitled "license.txt".

It is distributed WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, including warranties of fitness of purpose or
merchantability.  See the accompanying Martus License and
GPL license for more details on the required license terms
for this software.

You should have received a copy of the GNU General Public
License along with this program; if not, write to the Free
Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA 02111-1307, USA.

*/

package org.martus.client.test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

import org.martus.client.core.BulletinStore;
import org.martus.common.AttachmentProxy;
import org.martus.common.Bulletin;
import org.martus.common.BulletinSaver;
import org.martus.common.DatabaseKey;
import org.martus.common.MartusSecurity;
import org.martus.common.MockClientDatabase;
import org.martus.common.MockDatabase;
import org.martus.common.TestCaseEnhanced;

public class TestBulletinSaver extends TestCaseEnhanced
{
	public TestBulletinSaver(String name) throws Exception
	{
		super(name);
	}

	public void setUp() throws Exception
	{
		if(tempFile1 == null)
		{
			tempFile1 = createTempFile(sampleBytes1);
			tempFile2 = createTempFile(sampleBytes2);
			tempFile3 = createTempFile(sampleBytes3);
			tempFile4 = createTempFile(sampleBytes4);
			tempFile5 = createTempFile(sampleBytes5);
			tempFile6 = createTempFile(sampleBytes6);
		}
		proxy1 = new AttachmentProxy(tempFile1);
		proxy2 = new AttachmentProxy(tempFile2);
		proxy3 = new AttachmentProxy(tempFile3);
		proxy4 = new AttachmentProxy(tempFile4);
		proxy5 = new AttachmentProxy(tempFile5);
		proxy6 = new AttachmentProxy(tempFile6);

		if(security == null)
		{
			security = new MartusSecurity();
			security.createKeyPair(512);
		}

		if(store == null)
		{
			db = new MockClientDatabase();
			store = new BulletinStore(db);
			store.setSignatureGenerator(security);
		}
		store.deleteAllData();
	}

	public void testSaveToDatabase() throws Exception
	{
		assertEquals(0, db.getAllKeys().size());

		Bulletin b = store.createEmptyBulletin();
		b.set("summary", "New bulletin");
		BulletinSaver.saveToDatabase(b, db, store.mustEncryptPublicData(), security);
		DatabaseKey headerKey1 = new DatabaseKey(b.getBulletinHeaderPacket().getUniversalId());
		DatabaseKey dataKey1 = new DatabaseKey(b.getFieldDataPacket().getUniversalId());
		assertEquals("saved 1", 3, db.getAllKeys().size());
		assertEquals("saved 1 header key", true,db.doesRecordExist(headerKey1));
		assertEquals("saved 1 data key", true,db.doesRecordExist(dataKey1));

		// re-saving the same bulletin replaces the old one
		BulletinSaver.saveToDatabase(b, db, store.mustEncryptPublicData(), security);
		assertEquals("resaved 1", 3, db.getAllKeys().size());
		assertEquals("resaved 1 header key", true,db.doesRecordExist(headerKey1));
		assertEquals("resaved 1 data key", true,db.doesRecordExist(dataKey1));

		Bulletin b3 = store.loadFromDatabase(headerKey1);
		assertEquals("id", b.getLocalId(), b3.getLocalId());
		assertEquals("summary", b.get("summary"), b3.get("summary"));

		// unsaved bulletin changes should not be in the store
		Bulletin b2 = store.loadFromDatabase(headerKey1);
		b2.set("summary", "not saved yet");
		Bulletin b4 = store.loadFromDatabase(headerKey1);
		assertEquals("id", b.getLocalId(), b4.getLocalId());
		assertEquals("summary", b.get("summary"), b4.get("summary"));

		// saving a new bulletin with a non-empty id should retain that id
		b = store.createEmptyBulletin();
		BulletinSaver.saveToDatabase(b, db, store.mustEncryptPublicData(), security);
		assertEquals("saved another", 6, db.getAllKeys().size());
		assertEquals("old header key", true, db.doesRecordExist(headerKey1));
		assertEquals("old data key", true, db.doesRecordExist(dataKey1));
		DatabaseKey newHeaderKey = new DatabaseKey(b.getBulletinHeaderPacket().getUniversalId());
		DatabaseKey newDataKey = new DatabaseKey(b.getFieldDataPacket().getUniversalId());
		assertEquals("new header key", true, db.doesRecordExist(newHeaderKey));
		assertEquals("new data key", true, db.doesRecordExist(newDataKey));
	}

	public void testSaveToDatabaseWithPendingAttachment() throws Exception
	{
		Bulletin b = store.createEmptyBulletin();
		AttachmentProxy a = new AttachmentProxy(tempFile1);
		b.addPublicAttachment(a);
		String[] attachmentIds = b.getBulletinHeaderPacket().getPublicAttachmentIds();
		assertEquals("one attachment", 1, attachmentIds.length);
		BulletinSaver.saveToDatabase(b, db, store.mustEncryptPublicData(), security);
		assertEquals("saved", 4, db.getAllKeys().size());

		Bulletin got = store.loadFromDatabase(new DatabaseKey(b.getUniversalId()));
		assertEquals("id", b.getLocalId(), got.getLocalId());
		assertEquals("attachment count", b.getPublicAttachments().length, got.getPublicAttachments().length);
	}

	public void testSaveToDatabaseWithAttachment() throws Exception
	{
		Bulletin b = store.createEmptyBulletin();
		DatabaseKey key = new DatabaseKey(b.getUniversalId());
		b.addPublicAttachment(proxy1);
		b.addPublicAttachment(proxy2);
		BulletinSaver.saveToDatabase(b, db, store.mustEncryptPublicData(), security);
		assertEquals("saved", 5, db.getAllKeys().size());

		Bulletin got1 = store.loadFromDatabase(key);
		verifyLoadedBulletin("First load", b, got1);
	}

	public void testSaveToDatabaseAllPrivate() throws Exception
	{
		Bulletin somePublicDraft = store.createEmptyBulletin();
		somePublicDraft.setAllPrivate(false);
		somePublicDraft.setDraft();
		BulletinSaver.saveToDatabase(somePublicDraft, db, store.mustEncryptPublicData(), security);
		assertEquals("public draft was not encrypted?", true, somePublicDraft.getFieldDataPacket().isEncrypted());

		Bulletin allPrivateDraft = store.createEmptyBulletin();
		allPrivateDraft.setAllPrivate(true);
		allPrivateDraft.setDraft();
		BulletinSaver.saveToDatabase(allPrivateDraft, db, store.mustEncryptPublicData(), security);
		assertEquals("private draft was not encrypted?", true, allPrivateDraft.getFieldDataPacket().isEncrypted());

		Bulletin somePublicSealed = store.createEmptyBulletin();
		somePublicSealed.setAllPrivate(false);
		somePublicSealed.setSealed();
		BulletinSaver.saveToDatabase(somePublicSealed, db, store.mustEncryptPublicData(), security);
		assertEquals("public sealed was encrypted?", false, somePublicSealed.getFieldDataPacket().isEncrypted());

		Bulletin allPrivateSealed = store.createEmptyBulletin();
		allPrivateSealed.setAllPrivate(true);
		allPrivateSealed.setSealed();
		BulletinSaver.saveToDatabase(somePublicSealed, db, store.mustEncryptPublicData(), security);
		assertEquals("private sealed was encrypted?", true, allPrivateSealed.getFieldDataPacket().isEncrypted());
	}


	public void testReSaveToDatabaseWithAttachments() throws Exception
	{
		Bulletin b = store.createEmptyBulletin();
		DatabaseKey key = new DatabaseKey(b.getUniversalId());
		b.addPublicAttachment(proxy1);
		b.addPublicAttachment(proxy2);
		BulletinSaver.saveToDatabase(b, db, store.mustEncryptPublicData(), security);
		assertEquals("saved", 5, db.getAllKeys().size());
		Bulletin got1 = store.loadFromDatabase(key);
		BulletinSaver.saveToDatabase(got1, db, store.mustEncryptPublicData(), security);
		assertEquals("resaved", 5, db.getAllKeys().size());

		Bulletin got2 = store.loadFromDatabase(key);
		verifyLoadedBulletin("Reload after save", got1, got2);
	}

	public void testReSaveToDatabaseAddAttachments() throws Exception
	{
		Bulletin b = store.createEmptyBulletin();
		DatabaseKey key = new DatabaseKey(b.getUniversalId());
		b.addPublicAttachment(proxy1);
		b.addPublicAttachment(proxy2);
		b.addPrivateAttachment(proxy4);
		b.addPrivateAttachment(proxy5);
		BulletinSaver.saveToDatabase(b, db, store.mustEncryptPublicData(), security);
		Bulletin got1 = store.loadFromDatabase(key);

		got1.clear();
		got1.addPublicAttachment(proxy1);
		got1.addPublicAttachment(proxy2);
		got1.addPublicAttachment(proxy3);
		got1.addPrivateAttachment(proxy4);
		got1.addPrivateAttachment(proxy5);
		got1.addPrivateAttachment(proxy6);
		BulletinSaver.saveToDatabase(got1, db, store.mustEncryptPublicData(), security);
		assertEquals("resaved", 9, db.getAllKeys().size());

		Bulletin got3 = store.loadFromDatabase(key);
		verifyLoadedBulletin("Reload after save", got1, got3);

		String[] publicAttachmentIds = got3.getBulletinHeaderPacket().getPublicAttachmentIds();
		assertEquals("wrong public attachment count in bhp?", 3, publicAttachmentIds.length);
		String[] privateAttachmentIds = got3.getBulletinHeaderPacket().getPrivateAttachmentIds();
		assertEquals("wrong private attachment count in bhp?", 3, privateAttachmentIds.length);
	}

	public void testReSaveToDatabaseRemoveAttachment() throws Exception
	{
		Bulletin b = store.createEmptyBulletin();
		DatabaseKey key = new DatabaseKey(b.getUniversalId());
		b.addPublicAttachment(proxy1);
		b.addPublicAttachment(proxy2);
		b.addPrivateAttachment(proxy3);
		b.addPrivateAttachment(proxy4);
		BulletinSaver.saveToDatabase(b, db, store.mustEncryptPublicData(), security);
		assertEquals("saved key count", 7, db.getAllKeys().size());
		Bulletin got1 = store.loadFromDatabase(key);
		AttachmentProxy keep = got1.getPublicAttachments()[1];
		AttachmentProxy keepPrivate = got1.getPrivateAttachments()[1];

		got1.clear();
		got1.addPublicAttachment(keep);
		got1.addPrivateAttachment(keepPrivate);
		BulletinSaver.saveToDatabase(got1, db, store.mustEncryptPublicData(), security);
		assertEquals("resaved modified", 5, db.getAllKeys().size());

		Bulletin got3 = store.loadFromDatabase(key);
		verifyLoadedBulletin("Reload after save", got1, got3);

		String[] publicAttachmentIds = got3.getBulletinHeaderPacket().getPublicAttachmentIds();
		assertEquals("wrong public attachment count in bhp?", 1, publicAttachmentIds.length);
		String[] privateAttachmentIds = got3.getBulletinHeaderPacket().getPrivateAttachmentIds();
		assertEquals("wrong private attachment count in bhp?", 1, privateAttachmentIds.length);
	}


	protected void verifyLoadedBulletin(String tag, Bulletin original, Bulletin got) throws Exception
	{
		assertEquals(tag + " id", original.getUniversalId(), got.getUniversalId());
		AttachmentProxy[] originalAttachments = got.getPublicAttachments();
		assertEquals(tag + " wrong public attachment count?", original.getPublicAttachments().length, originalAttachments.length);
		verifyAttachments(tag + "public", got, originalAttachments);

		AttachmentProxy[] originalPrivateAttachments = got.getPrivateAttachments();
		assertEquals(tag + " wrong private attachment count?", original.getPrivateAttachments().length, originalPrivateAttachments.length);
		verifyAttachments(tag + "private", got, originalPrivateAttachments);
	}

	protected void verifyAttachments(String tag, Bulletin got, AttachmentProxy[] originalAttachments) throws Exception
	{
		for(int i=0; i < originalAttachments.length; ++i)
		{
			AttachmentProxy gotA = originalAttachments[i];
			String localId = gotA.getUniversalId().getLocalId();
			DatabaseKey key1 = new DatabaseKey(gotA.getUniversalId());
			assertEquals(tag + i + " missing original record?", true,  db.doesRecordExist(key1));

			File tempFile = File.createTempFile("$$$MartusTestBullSvAtt", null);
			tempFile.deleteOnExit();
			BulletinSaver.extractAttachmentToFile(db, gotA, security, tempFile);
			FileInputStream in = new FileInputStream(tempFile);
			byte[] gotBytes = new byte[in.available()];
			in.read(gotBytes);
			in.close();
			byte[] expectedBytes = null;
			if(localId.equals(proxy1.getUniversalId().getLocalId()))
				expectedBytes = sampleBytes1;
			else if(localId.equals(proxy2.getUniversalId().getLocalId()))
				expectedBytes = sampleBytes2;
			else if(localId.equals(proxy3.getUniversalId().getLocalId()))
				expectedBytes = sampleBytes3;
			else if(localId.equals(proxy4.getUniversalId().getLocalId()))
				expectedBytes = sampleBytes4;
			else if(localId.equals(proxy5.getUniversalId().getLocalId()))
				expectedBytes = sampleBytes5;
			else if(localId.equals(proxy6.getUniversalId().getLocalId()))
				expectedBytes = sampleBytes6;


			assertEquals(tag + i + "got wrong data length?", expectedBytes.length, gotBytes.length);
			assertEquals(tag + i + "got bad data?", true, Arrays.equals(gotBytes, expectedBytes));
			tempFile.delete();
		}
	}

	static File tempFile1;
	static File tempFile2;
	static File tempFile3;
	static File tempFile4;
	static File tempFile5;
	static File tempFile6;

	static final byte[] sampleBytes1 = {1,1,2,3,0,5,7,11};
	static final byte[] sampleBytes2 = {3,1,4,0,1,5,9,2,7};
	static final byte[] sampleBytes3 = {6,5,0,4,7,5,5,4,4,0};
	static final byte[] sampleBytes4 = {12,34,56};
	static final byte[] sampleBytes5 = {9,8,7,6,5};
	static final byte[] sampleBytes6 = {1,3,5,7,9,11,13};

	static AttachmentProxy proxy1;
	static AttachmentProxy proxy2;
	static AttachmentProxy proxy3;
	static AttachmentProxy proxy4;
	static AttachmentProxy proxy5;
	static AttachmentProxy proxy6;

	static MockDatabase db;
	static BulletinStore store;
	static MartusSecurity security;

}
