package org.martus.client;

import java.util.Iterator;
import java.util.Vector;

import javax.swing.table.AbstractTableModel;

import org.martus.client.MartusApp.ServerErrorException;
import org.martus.common.NetworkInterfaceConstants;
import org.martus.common.NetworkResponse;
import org.martus.common.UniversalId;
import org.martus.common.MartusCrypto.MartusSignatureException;

abstract public class RetrieveTableModel extends AbstractTableModel
{
	public RetrieveTableModel(MartusApp appToUse, UiProgressRetrieveSummariesDlg retriever)
	{
		app = appToUse;
		summaries = new Vector();
		store = app.getStore();
		retrieverDlg = retriever;
		allSummaries = new Vector();

	}

	abstract public void Initalize() throws ServerErrorException;

	public Vector getSummariesForBulletinsNotInStore(Vector allSummaries) 
	{
		Vector result = new Vector();
		Iterator iterator = allSummaries.iterator();
		while(iterator.hasNext())
		{
			BulletinSummary currentSummary = (BulletinSummary)iterator.next();
			UniversalId uid = UniversalId.createFromAccountAndLocalId(currentSummary.getAccountId(), currentSummary.getLocalId());
			if(store.findBulletinByUniversalId(uid) != null)
				continue;
			result.add(currentSummary);
		}
		return result;
	}

	public void setAllFlags(boolean flagState)
	{
		for(int i = 0; i < summaries.size(); ++i)
			((BulletinSummary)summaries.get(i)).setFlag(flagState);
		fireTableDataChanged();
	}

	public Vector getUniversalIdList()
	{
		Vector uidList = new Vector();

		for(int i = 0; i < summaries.size(); ++i)
		{
			BulletinSummary summary = (BulletinSummary)summaries.get(i);
			if(summary.getFlag())
			{
				UniversalId uid = UniversalId.createFromAccountAndLocalId(summary.getAccountId(), summary.getLocalId());
				uidList.add(uid);
			}
		}
		return uidList;

	}

	public int getRowCount()
	{
		return summaries.size();
	}

	public boolean isCellEditable(int row, int column)
	{
		if(column == 0)
			return true;

		return false;
	}

	public void getMySummaries() throws ServerErrorException
	{
		Vector summaryStrings = app.getMyServerBulletinSummaries();
		createSummariesFromStrings(app.getAccountId(), summaryStrings);
	}

	public void getMyDraftSummaries() throws ServerErrorException
	{
		Vector summaryStrings = app.getMyDraftServerBulletinSummaries();
		createSummariesFromStrings(app.getAccountId(), summaryStrings);
	}

	public void getFieldOfficeSealedSummaries(String fieldOfficeAccountId) throws ServerErrorException
	{
		try 
		{
			NetworkResponse response = app.getCurrentSSLServerProxy().getSealedBulletinIds(app.security, fieldOfficeAccountId);
			if(response.getResultCode().equals(NetworkInterfaceConstants.OK))
			{
				createSummariesFromStrings(fieldOfficeAccountId, response.getResultVector());
				return;
			}
		} 
		catch (MartusSignatureException e)
		{
			System.out.println("RetrieveTableModle.getFieldOfficeSealedSummaries: " + e);
		}
		throw new ServerErrorException();
	}

	public void getFieldOfficeDraftSummaries(String fieldOfficeAccountId) throws ServerErrorException
	{
		try 
		{
			NetworkResponse response = app.getCurrentSSLServerProxy().getDraftBulletinIds(app.security, fieldOfficeAccountId);
			if(response.getResultCode().equals(NetworkInterfaceConstants.OK))
			{
				createSummariesFromStrings(fieldOfficeAccountId, response.getResultVector());
				return;
			}
		} 
		catch (MartusSignatureException e)
		{
			System.out.println("MartusApp.getFieldOfficeDraftSummaries: " + e);
		}
		throw new ServerErrorException();
	}

	public void createSummariesFromStrings(String accountId, Vector summaryStrings)
		throws ServerErrorException 
	{
		RetrieveThread worker = new RetrieveThread(accountId, summaryStrings);
		worker.start();

		if(retrieverDlg == null)
			waitForThreadToTerminate(worker);
		else
			retrieverDlg.beginRetrieve();
		checkIfErrorOccurred();
	}

	public void checkIfErrorOccurred() throws ServerErrorException 
	{
		if(errorThrown != null)
			throw (errorThrown);
	}
	
	public void waitForThreadToTerminate(RetrieveThread worker) 
	{
		try 
		{
			worker.join();
		} 
		catch (InterruptedException e) 
		{
		}
	}

	class RetrieveThread extends Thread
	{
		public RetrieveThread(String account, Vector summarys)
		{
			accountId = account;
			summaryStrings = summarys;
		}
		
		public void run()
		{
			try 
			{
				Iterator iterator = summaryStrings.iterator();
				int count = 0;
				int maxCount = summaryStrings.size();
				while(iterator.hasNext())
				{
					String pair = (String)iterator.next();
					BulletinSummary bulletinSummary = app.createSummaryFromString(accountId, pair);
					allSummaries.add(bulletinSummary);
					if(retrieverDlg != null)
						retrieverDlg.updateBulletinCountMeter(++count, maxCount);
				}
			} catch (ServerErrorException e) 
			{
				errorThrown = e;
			}
			finishedRetrieve();
		}

		public void finishedRetrieve()
		{
			if(retrieverDlg != null)
				retrieverDlg.finishedRetrieve();
		}

		private String accountId;
		private Vector summaryStrings;	
	}

	public Vector getResults() throws ServerErrorException
	{
		if(errorThrown != null)
			throw (errorThrown);
		return allSummaries;	
	}

	MartusApp app;
	Vector summaries;
	BulletinStore store;
	private UiProgressRetrieveSummariesDlg retrieverDlg;
	protected Vector allSummaries;
	ServerErrorException errorThrown;
}
