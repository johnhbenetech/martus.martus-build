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

package org.martus.common;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Vector;

import org.martus.common.Base64.InvalidBase64Exception;
import org.martus.common.MartusCrypto.CryptoException;
import org.martus.common.Packet.InvalidPacketException;
import org.martus.common.Packet.SignatureVerificationException;
import org.martus.common.Packet.WrongPacketTypeException;



public class Bulletin implements BulletinConstants
{

	public static class DamagedBulletinException extends Exception
	{
	}

	public Bulletin(MartusCrypto securityToUse)
	{
		security = securityToUse;
		String accountId = security.getPublicKeyString();
		UniversalId headerUid = BulletinHeaderPacket.createUniversalId(accountId);
		UniversalId dataUid = FieldDataPacket.createUniversalId(accountId);
		UniversalId privateDataUid = FieldDataPacket.createUniversalId(accountId);

		createMemberVariables(headerUid, dataUid, privateDataUid);

		clear();
	}

	public MartusCrypto getSignatureGenerator()
	{
		return security;
	}

	public UniversalId getUniversalId()
	{
		return getBulletinHeaderPacket().getUniversalId();
	}

	public String getUniversalIdString()
	{
		return getUniversalId().toString();
	}

	public String getAccount()
	{
		return getBulletinHeaderPacket().getAccountId();
	}

	public String getLocalId()
	{
		return getBulletinHeaderPacket().getLocalId();
	}
	
	public void setIsValid(boolean isValid)
	{
		isValidFlag = isValid;
	}

	public boolean isValid()
	{
		return isValidFlag;
	}

	public long getLastSavedTime()
	{
		return getBulletinHeaderPacket().getLastSavedTime();
	}

	public boolean isDraft()
	{
		return getStatus().equals(STATUSDRAFT);
	}

	public boolean isSealed()
	{
		return getStatus().equals(STATUSSEALED);
	}

	public void setDraft()
	{
		setStatus(STATUSDRAFT);
	}

	public void setSealed()
	{
		setStatus(STATUSSEALED);
	}

	public void setStatus(String newStatus)
	{
		getBulletinHeaderPacket().setStatus(newStatus);
	}

	public String getStatus()
	{
		return getBulletinHeaderPacket().getStatus();
	}

	public void set(String fieldName, String value)
	{
		if(isStandardField(fieldName))
			fieldData.set(fieldName, value);
		else
			privateFieldData.set(fieldName, value);
	}

	public String get(String fieldName)
	{
		if(fieldName.equals(Bulletin.TAGSTATUS))
		{
			if(isDraft())
				return BulletinConstants.STATUSDRAFT;
			return BulletinConstants.STATUSSEALED;
		}
		if(isStandardField(fieldName))
			return fieldData.get(fieldName);
		else
			return privateFieldData.get(fieldName);
	}

	public void addPublicAttachment(AttachmentProxy a) throws
		IOException,
		MartusCrypto.EncryptionException
	{
		BulletinHeaderPacket bhp = getBulletinHeaderPacket();
		File rawFile = a.getFile();
		if(rawFile != null)
		{
			byte[] sessionKeyBytes = getSignatureGenerator().createSessionKey();
			AttachmentPacket ap = new AttachmentPacket(getAccount(), sessionKeyBytes, rawFile, getSignatureGenerator());
			bhp.addPublicAttachmentLocalId(ap.getLocalId());
			pendingPublicAttachments.add(ap);
			a.setUniversalIdAndSessionKey(ap.getUniversalId(), sessionKeyBytes);
		}
		else
		{
			bhp.addPublicAttachmentLocalId(a.getUniversalId().getLocalId());
		}

		getFieldDataPacket().addAttachment(a);
	}

	public void addPrivateAttachment(AttachmentProxy a) throws
		IOException,
		MartusCrypto.EncryptionException
	{
		BulletinHeaderPacket bhp = getBulletinHeaderPacket();
		File rawFile = a.getFile();
		if(rawFile != null)
		{
			byte[] sessionKeyBytes = getSignatureGenerator().createSessionKey();
			AttachmentPacket ap = new AttachmentPacket(getAccount(), sessionKeyBytes, rawFile, getSignatureGenerator());
			bhp.addPrivateAttachmentLocalId(ap.getLocalId());
			getPendingPrivateAttachments().add(ap);
			a.setUniversalIdAndSessionKey(ap.getUniversalId(), sessionKeyBytes);
		}
		else
		{
			bhp.addPrivateAttachmentLocalId(a.getUniversalId().getLocalId());
		}

		getPrivateFieldDataPacket().addAttachment(a);
	}

	public AttachmentProxy[] getPublicAttachments()
	{
		return getFieldDataPacket().getAttachments();
	}

	public AttachmentProxy[] getPrivateAttachments()
	{
		return getPrivateFieldDataPacket().getAttachments();
	}

	public void clear()
	{
		getBulletinHeaderPacket().clearAttachments();
		getFieldDataPacket().clearAll();
		getPrivateFieldDataPacket().clearAll();
		pendingPublicAttachments.clear();
		getPendingPrivateAttachments().clear();
		set(TAGENTRYDATE, getToday());
		set(TAGEVENTDATE, getFirstOfThisYear());
		setDraft();
	}

	public void clearPublicAttachments()
	{
		getBulletinHeaderPacket().removeAllPublicAttachments();
		getFieldDataPacket().clearAttachments();
	}

	public void clearPrivateAttachments()
	{
		getBulletinHeaderPacket().removeAllPrivateAttachments();
		getPrivateFieldDataPacket().clearAttachments();
	}

	public boolean contains(String lookFor)
	{
		String fields[] = fieldData.getFieldTags();
		String lookForLowerCase = lookFor.toLowerCase();
		for(int f = 0; f < fields.length; ++f)
		{
			String contents = get(fields[f]).toLowerCase();
			if(contents.indexOf(lookForLowerCase) >= 0)
				return true;
		}
		return false;
	}

	public boolean withinDates(String beginDate, String endDate)
	{
		String eventDate = fieldData.get(Bulletin.TAGEVENTDATE);
		String entryDate = fieldData.get(Bulletin.TAGENTRYDATE);
		if(eventDate.compareTo(beginDate) >= 0 && eventDate.compareTo(endDate) <= 0)
			return true;
		if(entryDate.compareTo(beginDate) >= 0 && entryDate.compareTo(endDate) <= 0)
			return true;

		return false;
	}

	public String getHQPublicKey()
	{
		return getBulletinHeaderPacket().getHQPublicKey();
	}

	public void setHQPublicKey(String key)
	{
		getBulletinHeaderPacket().setHQPublicKey(key);
		getFieldDataPacket().setHQPublicKey(key);
		getPrivateFieldDataPacket().setHQPublicKey(key);
	}

	public DatabaseKey getDatabaseKeyForLocalId(String localId)
	{
		UniversalId uidFdp = UniversalId.createFromAccountAndLocalId(getAccount(), localId);
		return new DatabaseKey(uidFdp);
	}

	public boolean isStandardField(String fieldName)
	{
		return getFieldDataPacket().fieldExists(fieldName);
	}

	public boolean isPrivateField(String fieldName)
	{
		return getPrivateFieldDataPacket().fieldExists(fieldName);
	}

	public int getFieldCount()
	{
		return fieldData.getFieldCount();
	}

	public static String[] getStandardFieldNames()
	{
		return new String[]
		{
			TAGLANGUAGE,

			TAGAUTHOR, TAGORGANIZATION,
			TAGTITLE, TAGLOCATION, TAGKEYWORDS,
			TAGEVENTDATE, TAGENTRYDATE,
			TAGSUMMARY, TAGPUBLICINFO,
		};
	}

	public static String[] getPrivateFieldNames()
	{
		return new String[]
		{
			TAGPRIVATEINFO,
		};
	}

	public static int getFieldType(String fieldName)
	{
		String lookFor = fieldName.toLowerCase();

		if(lookFor.equals(TAGSUMMARY) ||
				lookFor.equals(TAGPUBLICINFO) ||
				lookFor.equals(TAGPRIVATEINFO) )
			return MULTILINE;

		if(lookFor.equals(TAGEVENTDATE) ||
				lookFor.equals(TAGENTRYDATE) )
			return DATE;

		if(lookFor.equals(TAGLANGUAGE))
			return CHOICE;

		return NORMAL;
	}

	public static boolean isFieldEncrypted(String fieldName)
	{
		String lookFor = fieldName.toLowerCase();

		if(lookFor.equals(TAGPRIVATEINFO))
			return true;

		return false;
	}

	public boolean isAllPrivate()
	{

		BulletinHeaderPacket bhp = getBulletinHeaderPacket();
		if(!bhp.hasAllPrivateFlag())
		{
			FieldDataPacket fdp = getFieldDataPacket();
			bhp.setAllPrivate(fdp.isEncrypted());
		}
		return bhp.isAllPrivate();
	}

	public void setAllPrivate(boolean newValue)
	{
		getBulletinHeaderPacket().setAllPrivate(newValue);
	}

	public void pullDataFrom(Bulletin other, Database otherDatabase) throws
		CryptoException, 
		InvalidPacketException, 
		SignatureVerificationException, 
		WrongPacketTypeException, 
		IOException, 
		InvalidBase64Exception
	{
		this.clear();

		setDraft();
		setAllPrivate(other.isAllPrivate());

		{
			String fields[] = fieldData.getFieldTags();
			for(int f = 0; f < fields.length; ++f)
			{
				set(fields[f], other.get(fields[f]));
			}
		}

		{
			String privateFields[] = privateFieldData.getFieldTags();
			for(int f = 0; f < privateFields.length; ++f)
			{
				set(privateFields[f], other.get(privateFields[f]));
			}
		}

		MartusCrypto security = getSignatureGenerator();
		AttachmentProxy[] attachmentPublicProxies = other.getPublicAttachments();
		for(int aIndex = 0; aIndex < attachmentPublicProxies.length; ++aIndex)
		{
			AttachmentProxy ap = attachmentPublicProxies[aIndex];
			ap = getAsFileProxy(ap, otherDatabase, Bulletin.STATUSDRAFT, security);
			addPublicAttachment(ap);
		}

		AttachmentProxy[] attachmentPrivateProxies = other.getPrivateAttachments();
		for(int aIndex = 0; aIndex < attachmentPrivateProxies.length; ++aIndex)
		{
			AttachmentProxy ap = attachmentPrivateProxies[aIndex];
			ap = getAsFileProxy(ap, otherDatabase, Bulletin.STATUSDRAFT, security);
			addPrivateAttachment(ap);
		}

		pendingPublicAttachments.addAll(other.pendingPublicAttachments);
		getPendingPrivateAttachments().addAll(other.getPendingPrivateAttachments());
	}

	public AttachmentProxy getAsFileProxy(AttachmentProxy ap, Database otherDatabase, String status, MartusCrypto security)
		throws
			IOException,
			CryptoException,
			InvalidPacketException,
			SignatureVerificationException,
			WrongPacketTypeException,
			InvalidBase64Exception
	{
		if(ap.getFile() != null) 
			return ap;
		if(otherDatabase == null)
			return ap;
			
		DatabaseKey key = DatabaseKey.createKey(ap.getUniversalId(),status);
		InputStreamWithSeek packetIn = otherDatabase.openInputStream(key, security);
		if(packetIn == null)
			return ap;
			
		try
		{
			return MartusUtilities.createFileProxyFromAttachmentPacket(packetIn, ap, security);
		}
		finally
		{
			packetIn.close();
		}
	}

	static String getFirstOfThisYear()
	{
		GregorianCalendar cal = new GregorianCalendar();
		cal.set(GregorianCalendar.MONTH, 0);
		cal.set(GregorianCalendar.DATE, 1);
		DateFormat df = getStoredDateFormat();
		return df.format(cal.getTime());
	}

	public static String getLastDayOfThisYear()
	{
		GregorianCalendar cal = new GregorianCalendar();
		cal.set(GregorianCalendar.MONTH, 11);
		cal.set(GregorianCalendar.DATE, 31);
		DateFormat df = getStoredDateFormat();
		return df.format(cal.getTime());
	}

	public static String getToday()
	{
		DateFormat df = getStoredDateFormat();
		return df.format(new Date());
	}

	public static DateFormat getStoredDateFormat()
	{
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
		df.setLenient(false);
		return df;
	}

	public BulletinHeaderPacket getBulletinHeaderPacket()
	{
		return header;
	}

	public FieldDataPacket getFieldDataPacket()
	{
		return fieldData;
	}

	public FieldDataPacket getPrivateFieldDataPacket()
	{
		return privateFieldData;
	}

	private void createMemberVariables(UniversalId headerUid, UniversalId dataUid, UniversalId privateDataUid)
	{
		isValidFlag = true;
		fieldData = new FieldDataPacket(dataUid, getStandardFieldNames());
		fieldData.setEncrypted(true);
		privateFieldData = new FieldDataPacket(privateDataUid, getPrivateFieldNames());
		privateFieldData.setEncrypted(true);
		header = new BulletinHeaderPacket(headerUid);
		header.setFieldDataPacketId(dataUid.getLocalId());
		header.setPrivateFieldDataPacketId(privateDataUid.getLocalId());
		setPendingPublicAttachments(new Vector());
		setPendingPrivateAttachments(new Vector());
	}

	private void setPendingPublicAttachments(Vector pendingPublicAttachments)
	{
		this.pendingPublicAttachments = pendingPublicAttachments;
	}

	public Vector getPendingPublicAttachments()
	{
		return pendingPublicAttachments;
	}

	private void setPendingPrivateAttachments(Vector pendingPrivateAttachments)
	{
		this.pendingPrivateAttachments = pendingPrivateAttachments;
	}

	public Vector getPendingPrivateAttachments()
	{
		return pendingPrivateAttachments;
	}

	private boolean encryptedFlag;
	private boolean isFromDatabase;
	private boolean isValidFlag;
	private MartusCrypto security;
	private BulletinHeaderPacket header;
	private FieldDataPacket fieldData;
	private FieldDataPacket privateFieldData;
	private Vector pendingPublicAttachments;
	private Vector pendingPrivateAttachments;
}
