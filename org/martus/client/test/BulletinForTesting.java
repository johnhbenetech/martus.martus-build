/*

The Martus(tm) free, social justice documentation and
monitoring software. Copyright (C) 2001-2003, Beneficent
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

import java.io.IOException;

import org.martus.common.FieldSpec;
import org.martus.common.XmlWriterFilter;
import org.martus.common.bulletin.Bulletin;
import org.martus.common.crypto.MartusCrypto;
import org.martus.common.packet.BulletinHeaderPacket;
import org.martus.common.packet.FieldDataPacket;
import org.martus.common.packet.UniversalId;

public class BulletinForTesting extends Bulletin
{
	public BulletinForTesting(MartusCrypto securityToUse)
	{
		super(securityToUse);
	}
	
	public static void clearShoulds()
	{
		shouldCreateUnknownTagInHeader = false;
		shouldCreateUnknownTagInPublicSection = false;
		shouldCreateUnknownTagInPrivateSection = false;
		shouldCreateUnknownStuffInCustomField = false;
	}
	
	protected BulletinHeaderPacket createHeaderPacket(UniversalId headerUid)
	{
		if(shouldCreateUnknownTagInHeader)
			return new HeaderPacketWithUnknownTag(headerUid);
			
		return super.createHeaderPacket(headerUid);
	}
	
	static class HeaderPacketWithUnknownTag extends BulletinHeaderPacket
	{
		public HeaderPacketWithUnknownTag(UniversalId universalIdToUse)
		{
			super(universalIdToUse);
		}

		protected void internalWriteXml(XmlWriterFilter dest) throws IOException
		{
			super.internalWriteXml(dest);
			writeElement(dest, "UnknownTag", "");
		}
	}
	
	static class FieldDataPacketWithUnknownCustomField extends FieldDataPacket
	{
		public FieldDataPacketWithUnknownCustomField(UniversalId universalIdToUse, FieldSpec[] fieldSpecsToUse)
		{
			super(universalIdToUse, customFieldSpecs);
		}
		
		protected String getFieldListString()
		{
			String fields = super.getFieldListString();
			int insertAt = fields.indexOf("!");
			fields = fields.substring(0, insertAt) + ",more" + fields.substring(insertAt+1);
			return fields;
		}

		static FieldSpec[] customFieldSpecs = {
			new FieldSpec("language"), 
			new FieldSpec("entrydate"),
			new FieldSpec("author"),
			new FieldSpec("title"),
			new FieldSpec("custom,Custom"),
			new FieldSpec("extra,Bad Custom!"),
		};
	}

	static class FieldDataPacketWithUnknownTag extends FieldDataPacket
	{
		public FieldDataPacketWithUnknownTag(UniversalId universalIdToUse, FieldSpec[] fieldSpecsToUse)
		{
			super(universalIdToUse, fieldSpecsToUse);
		}

		protected void internalWriteXml(XmlWriterFilter dest) throws IOException
		{
			super.internalWriteXml(dest);
			writeElement(dest, "UnknownTag", "");
		}
	}
	
	protected FieldDataPacket createPublicFieldDataPacket(
		UniversalId dataUid,
		FieldSpec[] publicFieldSpecs)
	{
		if(shouldCreateUnknownTagInPublicSection)
			return new FieldDataPacketWithUnknownTag(dataUid, publicFieldSpecs);
			
		if(shouldCreateUnknownStuffInCustomField)
			return new FieldDataPacketWithUnknownCustomField(dataUid, publicFieldSpecs);
			
		return super.createPublicFieldDataPacket(dataUid, publicFieldSpecs);
	}
	protected FieldDataPacket createPrivateFieldDataPacket(
		UniversalId privateDataUid,
		FieldSpec[] privateFieldSpecs)
	{
		if(shouldCreateUnknownTagInPrivateSection)
			return new FieldDataPacketWithUnknownTag(privateDataUid, privateFieldSpecs);
		
		return super.createPrivateFieldDataPacket(
			privateDataUid,
			privateFieldSpecs);
	}


	static boolean shouldCreateUnknownTagInHeader;
	static boolean shouldCreateUnknownTagInPublicSection;
	static boolean shouldCreateUnknownTagInPrivateSection;
	static boolean shouldCreateUnknownStuffInCustomField;
}
