package org.martus.server.forclients;

import java.util.Vector;

import org.martus.common.MartusCrypto;

public interface ServerForClientsInterface
{
	public String getPublicCode(String clientId);
	public void logging(String message);
	public void clientConnectionStart();
	public void clientConnectionExit();
	public MartusCrypto getSecurity();
	public String ping();
	public String deleteDraftBulletins(String myAccountId, String[] idList);
	public Vector getBulletinChunk(String myAccountId, String authorAccountId, String bulletinLocalId, int chunkOffset, int maxChunkSize);
	public Vector getNews(String myAccountId, String versionLabel, String versionBuildDate);
	public Vector getPacket(String myAccountId, String authorAccountId, String bulletinLocalId, String packetLocalId);
	public Vector getServerCompliance();
	public Vector listMySealedBulletinIds(String authorAccountId, Vector retrieveTags);
	public String putBulletinChunk(String myAccountId, String authorAccountId, String bulletinLocalId, int totalSize, int chunkOffset, int chunkSize, String data);
	public String putContactInfo(String myAccountId, Vector parameters);
	public String requestUploadRights(String authorAccountId, String tryMagicWord);
	public String uploadBulletinChunk(String authorAccountId, String bulletinLocalId, int totalSize, int chunkOffset, int chunkSize, String data, String signature);
	public Vector legacyListFieldOfficeSealedBulletinIds(String hqAccountId, String authorAccountId);
	public Vector listFieldOfficeAccounts(String hqAccountId);
	public Vector listFieldOfficeDraftBulletinIds(String myAccountId, String authorAccountId, Vector retrieveTags);
	public Vector listFieldOfficeSealedBulletinIds(String myAccountId, String authorAccountId, Vector retrieveTags);
	public Vector listMyDraftBulletinIds(String authorAccountId, Vector retrieveTags);
}
