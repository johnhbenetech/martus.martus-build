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

package org.martus.common.database;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.martus.common.MartusUtilities;
import org.martus.common.database.FileDatabase.MissingAccountMapSignatureException;

public class MockServerDatabase extends MockDatabase
{
	public void verifyAccountMap() throws MartusUtilities.FileVerificationException, MissingAccountMapSignatureException
	{
	}

	public void deleteAllData()
	{
		sealedPacketMap = new TreeMap();
		draftPacketMap = new TreeMap();
		super.deleteAllData();
	}

	public int getSealedRecordCount()
	{
		return getAllSealedKeys().size();
	}

	synchronized void internalDiscardRecord(DatabaseKey key)
	{
		Map map = getPacketMapFor(key);
		map.remove(key);
	}

	synchronized Set internalGetAllKeys()
	{
		Set keys = new HashSet();
		keys.addAll(getAllSealedKeys());
		keys.addAll(getAllDraftKeys());
		return keys;
	}

	synchronized void addKeyToMap(DatabaseKey key, String record)
	{
		getPacketMapFor(key).put(key, record);
	}

	synchronized String readRecord(DatabaseKey key)
	{
		if(isHidden(key))
			return null;

		Map map = getPacketMapFor(key);
		return (String)map.get(key);
	}

	Set getAllSealedKeys()
	{
		return sealedPacketMap.keySet();
	}

	Set getAllDraftKeys()
	{
		return draftPacketMap.keySet();
	}

	Map getPacketMapFor(DatabaseKey key)
	{
		Map map = sealedPacketMap;
		if(key.isDraft())
			map = draftPacketMap;
		return map;
	}

	Map sealedPacketMap;
	Map draftPacketMap;
}
