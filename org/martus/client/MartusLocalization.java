package org.martus.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import org.martus.common.UnicodeReader;
import org.martus.common.UnicodeWriter;


public class MartusLocalization
{
    public static void main (String args[])
	{
		if(args.length != 2)
		{
			System.out.println("If you specify a language code and output filename, " +
								"this will write out a file");
			System.out.println("that contains all the existing translations " +
								"for language xx, plus placeholder");
			System.out.println("tags for all the untranslated strings.");
			System.out.println("Example of a language codes: es = Spanish");
			System.exit(1);
		}

		String languageCode = args[0].toLowerCase();
		if(languageCode.length() != 2 ||
			!Character.isLetter(languageCode.charAt(0)) ||
			!Character.isLetter(languageCode.charAt(1)))
		{
			System.out.println("Invalid language code. Must be two letters (e.g. 'es')");
			System.exit(2);
		}

		System.out.println("Exporting translations for: " + languageCode);
		MartusLocalization bd = new MartusLocalization();
		bd.loadTranslationFile(languageCode);
		Vector keys = bd.getAllTranslationStrings(languageCode);

		final String NEWLINE = System.getProperty("line.separator");
		try
		{
			UnicodeWriter writer = new UnicodeWriter(new File(args[1]));
			for(int i = 0; i < keys.size(); ++i)
				writer.write(keys.get(i) + NEWLINE);

			writer.close();
			System.out.println("Success");
		}
		catch(Exception e)
		{
			System.out.println("FAILED: " + e);
			System.exit(3);
		}

    }

    public MartusLocalization()
    {
		languageTranslationsMap = new TreeMap();
		loadEnglishTranslations();
	}

	public boolean isLanguageLoaded(String languageCode)
	{
		if(getStringMap(languageCode) == null)
			return false;

		return true;
	}

	public Map getStringMap(String languageCode)
	{
		return (Map)languageTranslationsMap.get(languageCode);
	}

	public void addTranslation(String languageCode, String translation)
	{
		if(translation == null)
			return;

		Map stringMap = getStringMap(languageCode);
		if(stringMap == null)
			return;

		int endKey = translation.indexOf('=');
		if(endKey < 0)
			return;

		String key = translation.substring(0,endKey);
		String value = translation.substring(endKey + 1, translation.length());
		stringMap.put(key, value);
	}

	public Map createStringMap(String languageCode)
	{
		if(!isLanguageLoaded(languageCode))
			languageTranslationsMap.put(languageCode, new TreeMap());

		return getStringMap(languageCode);
	}

	public String getLabel(String languageCode, String category, String tag, String defaultResult)
	{
		return getLabel(languageCode, category + ":" + tag, defaultResult);
	}

	private String getLabel(String languageCode, String key, String defaultResult)
	{
		String result = null;
		Map stringMap = getStringMap(languageCode);
		if(stringMap != null)
			result = (String)stringMap.get(key);
		if(result == null && !languageCode.equals(ENGLISH))
			result = "<" + getLabel(ENGLISH, key, defaultResult) + ">";
		if(result == null)
			result = defaultResult;
		return result;
	}

	private ChoiceItem getLanguageChoiceItem(String filename)
	{
		String code = getLanguageCodeFromFilename(filename);
		String name = getLabel(ENGLISH, "language", code, "Unknown: " + code);
		return new ChoiceItem(code, name);
	}

	public ChoiceItem[] getUiLanguages(String translationsDirectory)
	{
		Vector languages = new Vector();
		languages.addElement(new ChoiceItem(ENGLISH, getLabel(ENGLISH, "language", ENGLISH, "English")));
		String spanishFileName = "Martus-es.mtf";
		if(getClass().getResource(spanishFileName) != null)
		{
			languages.addElement(getLanguageChoiceItem(spanishFileName));
		}
		File dir = new File(translationsDirectory);
		String[] languageFiles = dir.list(new LanguageFilenameFilter());

		for(int i=0;i<languageFiles.length;++i)
		{
			languages.addElement(getLanguageChoiceItem(languageFiles[i]));
		}

		return (ChoiceItem[])(languages.toArray((Object[])(new ChoiceItem[0])));
	}

	public String getLanguageCodeFromFilename(String filename)
	{
		if(!isLanguageFile(filename))
			return "";

		int codeStart = filename.indexOf('-') + 1;
		int codeEnd = filename.indexOf('.');
		return filename.substring(codeStart, codeEnd);
	}

	private static boolean isLanguageFile(String filename)
	{
		return (filename.startsWith("Martus-") && filename.endsWith(".mtf"));
	}

	private static class LanguageFilenameFilter implements FilenameFilter
	{
		public boolean accept(File dir, String name)
		{
			return MartusLocalization.isLanguageFile(name);
		}
	}

	public static String getDefaultUiLanguage()
	{
		return ENGLISH;
	}

	public static ChoiceItem[] getDateFormats()
	{
		return new ChoiceItem[]
		{
			MDY_SLASH,
			DMY_SLASH,
			DMY_DOT
		};
	}

	public static String getDefaultDateFormatCode()
	{
		return MDY_SLASH.getCode();
	}

	public static String getMdyOrder(String format)
	{
		boolean foundM;
		boolean foundD;
		boolean foundY;

		String result = "";
		format = format.toLowerCase();
		for(int i = 0; i < format.length(); ++i)
		{
			char c = format.charAt(i);
			if( (c == 'm' || c == 'd' || c == 'y') && (result.indexOf(c) < 0) )
				result += c;
		}

		return result;
	}

	public Vector getAllTranslationStrings(String languageCode)
	{
		Map requestedMap = createStringMap(languageCode);

		Vector strings = new Vector();
		Map englishMap = getStringMap(ENGLISH);
		Set englishKeys = englishMap.keySet();
		SortedSet sorted = new TreeSet(englishKeys);
		Iterator it = sorted.iterator();
		while(it.hasNext())
		{
			String key = (String)it.next();
			String value = getLabel(languageCode, key, "???");
			strings.add(key + "=" + value);
		}
		return strings;
	}

	public void loadTranslationFile(String languageCode)
	{
		InputStream transStream = null;
		String fileShortName = "Martus-" + languageCode + ".mtf";
		File file = new File(MartusApp.getTranslationsDirectory(), fileShortName);
		try
		{
			if(file.exists())
			{
				transStream = new FileInputStream(file);
			}
			else
			{
				transStream = getClass().getResourceAsStream(fileShortName);
			}
			if(transStream == null)
			{
				return;
			}
			loadTranslations(languageCode, transStream);
		}

		catch (IOException e)
		{
			System.out.println("BulletinDisplay.loadTranslationFile " + e);
		}
	}

	public void loadTranslations(String languageCode, InputStream inputStream)
	{
		createStringMap(languageCode);
		try
		{
			UnicodeReader reader = new UnicodeReader(inputStream);
			String translation;
			while(true)
			{
				translation = reader.readLine();
				if(translation == null)
					break;
				addTranslation(languageCode, translation);
			}
			reader.close();
		}
		catch (IOException e)
		{
			System.out.println("BulletinDisplay.loadTranslations " + e);
		}
	}

	private void addEnglishTranslation(String translation)
	{
		addTranslation(ENGLISH, translation);
	}

	public void loadEnglishTranslations()
	{
		createStringMap(ENGLISH);
		addEnglishTranslation("wintitle:main=Martus Human Rights Bulletin System");
		addEnglishTranslation("wintitle:create=Create Bulletin");
		addEnglishTranslation("wintitle:sending=Sending to Server");
		addEnglishTranslation("wintitle:options=Options");
		addEnglishTranslation("wintitle:HelpDefaultDetails=Help on Bulletin Details Field Default Content");
		addEnglishTranslation("wintitle:MartusSignIn=Martus SignIn");
		addEnglishTranslation("wintitle:confirmsend=Confirm Send Bulletin");
		addEnglishTranslation("wintitle:confirmretrieve=Confirm Retrieve Bulletins");
		addEnglishTranslation("wintitle:confirmDiscardDraftBulletin=Confirm Delete Draft Bulletin");
		addEnglishTranslation("wintitle:confirmDiscardSealedBulletin=Confirm Delete Sealed Bulletin");
		addEnglishTranslation("wintitle:confirmdeletefolder=Confirm Delete Folder");
		addEnglishTranslation("wintitle:confirmRemoveAttachment=Confirm Remove Attachments");
		addEnglishTranslation("wintitle:confirmOverWriteExistingFile=Confirm OverWrite Existing File");
		addEnglishTranslation("wintitle:confirmCancelBulletinEdit=Cancel Bulletin Edit");
		addEnglishTranslation("wintitle:confirmSetImportPublicKey=Confirm Import of Public Key");
		addEnglishTranslation("wintitle:confirmWarningSwitchToNormalKeyboard=Security Warning");
		addEnglishTranslation("wintitle:confirmDeleteDiscardedBulletinWithCopies=Confirm Delete Bulletin");
		addEnglishTranslation("wintitle:confirmClearHQInformation=Confirm Removal of Headquarters");
		addEnglishTranslation("wintitle:confirmCloneMySealedAsDraft=Confirm Create Copy of Sealed Bulletin");
		addEnglishTranslation("wintitle:confirmCloneBulletinAsMine=Confirm Create Copy of Someone Else's Bulletin");
		addEnglishTranslation("wintitle:confirmDeleteDiscardedDraftBulletinWithOutboxCopy=Confirm Delete Draft Bulletin");
		addEnglishTranslation("wintitle:confirmPrinterWarning=Print Configuration Warning");

		addEnglishTranslation("wintitle:notifyDropNotAllowed=Cannot Move Bulletin");
		addEnglishTranslation("wintitle:notifyDropError=Error Moving Bulletin");
		addEnglishTranslation("wintitle:notifyretrieveworked=Retrieve Bulletins");
		addEnglishTranslation("wintitle:notifyretrievefailed=Retrieve Bulletins");
		addEnglishTranslation("wintitle:notifyretrievenothing=Retrieve Bulletins");
		addEnglishTranslation("wintitle:notifyconfignoserver=Unable to Connect to Server");
		addEnglishTranslation("wintitle:notifyretrievenoserver=Retrieve Bulletins");
		addEnglishTranslation("wintitle:notifypasswordsdontmatch=Invalid Setup Information");
		addEnglishTranslation("wintitle:notifyusernamessdontmatch=Invalid Setup Information");
		addEnglishTranslation("wintitle:notifyUserNameBlank=Invalid Setup Information");
		addEnglishTranslation("wintitle:notifyPasswordInvalid=Invalid Setup Information");
		addEnglishTranslation("wintitle:notifyPasswordMatchesUserName=Invalid Setup Information");
		addEnglishTranslation("wintitle:notifyincorrectsignin=Incorrect Signin");
		addEnglishTranslation("wintitle:notifyuploadreminder=Upload Reminder");
		addEnglishTranslation("wintitle:notifyuploadrejected=Error Sending Bulletin");
		addEnglishTranslation("wintitle:notifycorruptconfiginfo=Error Loading Configuration File");
		addEnglishTranslation("wintitle:notifyservercodewrong=Incorrect Server Public Code");
		addEnglishTranslation("wintitle:notifyserverinfoinvalid=Server Response Invalid");
		addEnglishTranslation("wintitle:notifyserverok=Server Selection Complete");
		addEnglishTranslation("wintitle:notifymagicwordok=Upload Permission Granted");
		addEnglishTranslation("wintitle:notifymagicwordrejected=Upload Permission Rejected");
		addEnglishTranslation("wintitle:notifyRewriteKeyPairFailed=Error Changing User Name or Password");
		addEnglishTranslation("wintitle:notifyRewriteKeyPairWorked=Changed User Name or Password");
		addEnglishTranslation("wintitle:notifyUnableToSaveAttachment=Saving Attachment Failed");
		addEnglishTranslation("wintitle:notifySearchFailed=Search Results");
		addEnglishTranslation("wintitle:notifySearchFound=Search Results");
		addEnglishTranslation("wintitle:notifyServerError=Server Error");
		addEnglishTranslation("wintitle:notifyFoundOrphans=Recovered Lost Bulletins");
		addEnglishTranslation("wintitle:notifyFoundDamagedBulletins=Detected Damaged Bulletins");
		addEnglishTranslation("wintitle:notifyErrorSavingState=Error Saving State");
		addEnglishTranslation("wintitle:notifyExportPublicInfo=Account Information Exported");
		addEnglishTranslation("wintitle:notifyPublicInfoFileError=Error Importing Public Information");
		addEnglishTranslation("wintitle:notifyAccountCodeWrong=Incorrect Public Code");
		addEnglishTranslation("wintitle:notifyErrorSavingConfig=Error Saving Configuration File");
		addEnglishTranslation("wintitle:notifyWarningBetaCopy=Security Warning");
		addEnglishTranslation("wintitle:notifyBetaExpired=Security Warning Beta Expired");
		addEnglishTranslation("wintitle:notifyAuthenticateServerFailed=Security Alert!");
		addEnglishTranslation("wintitle:notifyWelcomeToMartus=Welcome To Martus");
		addEnglishTranslation("wintitle:notifyUnexpectedError=Unexpected Error");

		addEnglishTranslation("wintitle:inputservername=Server Name");
		addEnglishTranslation("wintitle:inputserverpubliccode=Server Identification");
		addEnglishTranslation("wintitle:inputsearch=Search");
		addEnglishTranslation("wintitle:inputservermagicword=Request Upload Permission");
		addEnglishTranslation("wintitle:inputexportPublicInfo=Export Public Account Information");
		addEnglishTranslation("wintitle:inputImportPublicCode=Import Public Account Information");

		addEnglishTranslation("wintitle:setupsignin=Martus Setup Signin");
		addEnglishTranslation("wintitle:setupcontact=Martus Setup Contact Information");
		addEnglishTranslation("wintitle:BulletinDetails=Details Field Default Content");
		addEnglishTranslation("wintitle:retrieve=Retrieve Bulletins");
		addEnglishTranslation("wintitle:RetrieveDrafts=Retrieve Draft Bulletins");
		addEnglishTranslation("wintitle:retrieveHQ=Retrieve Field Desk Sealed Bulletins");
		addEnglishTranslation("wintitle:retrieveHQDrafts=Retrieve Field Desk Draft Bulletins");
		addEnglishTranslation("wintitle:about=About Martus");
		addEnglishTranslation("wintitle:AccountInfo=Account Information");
		addEnglishTranslation("wintitle:Help=Help on Martus");
		addEnglishTranslation("wintitle:RetrieveMySealedBulletinProgress=Retrieving Bulletins");
		addEnglishTranslation("wintitle:RetrieveMyDraftBulletinProgress=Retrieving Bulletins");
		addEnglishTranslation("wintitle:RetrieveHQSealedBulletinProgress=Retrieving Bulletins");
		addEnglishTranslation("wintitle:RetrieveHQDraftBulletinProgress=Retrieving Bulletins");
		addEnglishTranslation("wintitle:RetrieveMySealedBulletinSummaries=Retrieving Bulletin Summaries");
		addEnglishTranslation("wintitle:RetrieveMyDraftBulletinSummaries=Retrieving Bulletin Summaries");
		addEnglishTranslation("wintitle:RetrieveHQSealedBulletinSummaries=Retrieving Bulletin Summaries");
		addEnglishTranslation("wintitle:RetrieveHQDraftBulletinSummaries=Retrieving Bulletin Summaries");

		addEnglishTranslation("button:file=File");
		addEnglishTranslation("button:edit=Edit");
		addEnglishTranslation("button:help=Help");
		addEnglishTranslation("button:create=Create");
		addEnglishTranslation("button:edit=Edit");
		addEnglishTranslation("button:search=Search");
		addEnglishTranslation("button:print=Print");
		addEnglishTranslation("button:tools=Tools");
		addEnglishTranslation("button:options=Options");
		addEnglishTranslation("button:connectserver=Connect");
		addEnglishTranslation("button:send=Send");
		addEnglishTranslation("button:savedraft=Save Draft");
		addEnglishTranslation("button:ok=OK");
		addEnglishTranslation("button:inputservernameok=OK");
		addEnglishTranslation("button:inputserverpubliccodeok=OK");
		addEnglishTranslation("button:inputsearchok=Search");
		addEnglishTranslation("button:inputservermagicwordok=OK");
		addEnglishTranslation("button:inputexportPublicInfook=Export");
		addEnglishTranslation("button:inputImportPublicCodeok=Import");
		
		addEnglishTranslation("button:cancel=Cancel");
		addEnglishTranslation("button:browse=Browse...");
		addEnglishTranslation("button:yes=Yes");
		addEnglishTranslation("button:no=No");
		addEnglishTranslation("button:retrieve=Retrieve");
		addEnglishTranslation("button:checkall=Check All");
		addEnglishTranslation("button:uncheckall=Uncheck All");
		addEnglishTranslation("button:addattachment=Add Attachment");
		addEnglishTranslation("button:removeattachment=Remove Attachment");
		addEnglishTranslation("button:attachmentlabel=Attachment Name");
		addEnglishTranslation("button:saveattachment=Save Attachment");
		addEnglishTranslation("button:VirtualKeyboardSwitchToNormal=Switch to using regular keyboard");
		addEnglishTranslation("button:VirtualKeyboardSwitchToVirtual=Switch to using on-screen keyboard");
		addEnglishTranslation("button:DownloadableSummaries=Show bulletins that are only on the server.");
		addEnglishTranslation("button:AllSummaries=Show all bulletins on this server and on this computer.");

		
		addEnglishTranslation("button:FolderTreeRoot=Folders");

		addEnglishTranslation("menu:exit=Exit");
		addEnglishTranslation("menu:newFolder=Add new Folder");
		addEnglishTranslation("menu:createBulletin=Create new Bulletin");
		addEnglishTranslation("menu:editBulletin=Edit Bulletin");
		addEnglishTranslation("menu:printBulletin=Print Bulletin");
		addEnglishTranslation("menu:language=Language");
		addEnglishTranslation("menu:dateformat=Date Format");
		addEnglishTranslation("menu:contactinfo=Contact Information");
		addEnglishTranslation("menu:serverinfo=Server Configuration");
		addEnglishTranslation("menu:bulletinDetails=Details Field Default Content");
		addEnglishTranslation("menu:changeUserNamePassword=Change User Name or Password");
		addEnglishTranslation("menu:retrieve=Retrieve Bulletins");
		addEnglishTranslation("menu:RetrieveDrafts=Retrieve Draft Bulletins");
		addEnglishTranslation("menu:retrieveHQ=Retrieve Field Desk Bulletins");
		addEnglishTranslation("menu:retrieveHQDrafts=Retrieve Field Desk Draft Bulletins");
		addEnglishTranslation("menu:exportPublicAccountInfo=Export Public Information");
		addEnglishTranslation("menu:importPublicAccountInfo=Import Public Information");
		addEnglishTranslation("menu:clearPublicAccountInfo=Remove Existing Headquarters");
		addEnglishTranslation("menu:cut=Cut");
		addEnglishTranslation("menu:copy=Copy");
		addEnglishTranslation("menu:paste=Paste");
		addEnglishTranslation("menu:cutbulletin=Cut Bulletin");
		addEnglishTranslation("menu:copybulletin=Copy Bulletin");
		addEnglishTranslation("menu:pastebulletin=Paste Bulletin");
		addEnglishTranslation("menu:search=Search");
		addEnglishTranslation("menu:discardbulletin=Discard Bulletin");
		addEnglishTranslation("menu:deletebulletin=Delete Bulletin");
		addEnglishTranslation("menu:delete=Delete");
		addEnglishTranslation("menu:selectall=Select All");
		addEnglishTranslation("menu:about=About Martus");
		addEnglishTranslation("menu:account=Account Details");
		addEnglishTranslation("menu:helpMessage=Help");

		addEnglishTranslation("language:en=English");
		addEnglishTranslation("language:es=Spanish");
		addEnglishTranslation("language:fr=French");
		addEnglishTranslation("language:de=German");
		addEnglishTranslation("language:ru=Russian");
		addEnglishTranslation("language:ta=Tamil");
		addEnglishTranslation("language:eo=Esperanto");
		addEnglishTranslation("language:si=Sinhalese");

		addEnglishTranslation("field:aboutDlgVersionInfo=Martus Beta Version ");
		addEnglishTranslation("field:aboutDlgCopyright=Copyright 2002 Benetech");
		addEnglishTranslation("field:aboutDlgLine3=Martus Software is developed by Benetech in partnership with the information program ");
		addEnglishTranslation("field:aboutDlgLine4=of the Open Society Institute and the American Association for the Advancement of Science.");
		addEnglishTranslation("field:allprivate=Keep ALL Information Private");
		addEnglishTranslation("field:language=Language");
		addEnglishTranslation("field:author=Source");
		addEnglishTranslation("field:title=Title");
		addEnglishTranslation("field:location=Location");
		addEnglishTranslation("field:eventdate=Date of Event");
		addEnglishTranslation("field:entrydate=Date Entered");
		addEnglishTranslation("field:keywords=Keywords");
		addEnglishTranslation("field:summary=Summary");
		addEnglishTranslation("field:TemplateDetails=Details");
		addEnglishTranslation("field:publicinfo=Details");
		addEnglishTranslation("field:privateinfo=Private");
		addEnglishTranslation("field:status=Status");
		addEnglishTranslation("field:connecting=Connecting...");
		addEnglishTranslation("field:authorizing=Authorizing...");
		addEnglishTranslation("field:sending=Sending...");
		addEnglishTranslation("field:confirming=Confirming...");
		addEnglishTranslation("field:disconnecting=Disconnecting...");
		addEnglishTranslation("field:dateformat=Date Format");
		addEnglishTranslation("field:attachments=Attachments");
		addEnglishTranslation("field:publicsection=Public Information");
		addEnglishTranslation("field:privatesection=Private Information");
		addEnglishTranslation("field:MayBeDamaged=Warning: Portions may be missing or damaged");
		addEnglishTranslation("field:retrieveflag=Retrieve?");
		addEnglishTranslation("field:waitingForKeyPairGeneration=Please wait a minute while your account is being created...");
		addEnglishTranslation("field:waitingForBulletinsToLoad=Loading Martus.  Please wait...");
		addEnglishTranslation("field:HelpDefaultDetails=Enter questions, details, or other information your organization wants to have answered in future bulletins created.");
		addEnglishTranslation("field:HelpExampleDefaultDetails=Example:");
		addEnglishTranslation("field:HelpExample1DefaultDetails=Were there any witnesses?");
		addEnglishTranslation("field:HelpExample2DefaultDetails=Describe the weather.");
		addEnglishTranslation("field:HelpExampleEtcDefaultDetails=etc...");
		addEnglishTranslation("field:PublicInformationFiles=Public Information Files");
		addEnglishTranslation("field:NormalKeyboardMsg1=Remember: Entering your password using the regular keyboard may reduce security.");
		addEnglishTranslation("field:NormalKeyboardMsg2=For maximum security switch to the on-screen keyboard.");
		addEnglishTranslation("field:RetrieveSummariesMessage=All bulletins retrieved will still remain on the server.\nYou can only retrieve bulletins that are not currently on your computer.");
		addEnglishTranslation("field:ContactInfoRequired=This information identifies your organization.\nCurrently the only required field is Source, which gets displayed in every bulletin you create.");
		addEnglishTranslation("field:ContactInfoDescriptionOfFields=All other fields currently are stored on disk but are not used.");
		addEnglishTranslation("field:ContactInfoFutureUseOfFields=In the future this information will be available to anyone who can view your public bulletin information.\nThis allows people to contact you for further information.");
		addEnglishTranslation("field:ContactInfoUpdateLater=You can change any of this information later, by choosing Options/Contact Info.");
		addEnglishTranslation("field:UploadingSealedBulletin=Uploading Sealed Bulletin");
		addEnglishTranslation("field:UploadingDraftBulletin=Uploading Draft Bulletin");
		addEnglishTranslation("field:StatusReady=Ready");
		addEnglishTranslation("field:RetrieveMySealedBulletinProgress=Retrieving Sealed Bulletins");
		addEnglishTranslation("field:RetrieveMyDraftBulletinProgress=Retrieving Draft Bulletins");
		addEnglishTranslation("field:RetrieveHQSealedBulletinProgress=Retrieving Field Desk Sealed Bulletins");
		addEnglishTranslation("field:RetrieveHQDraftBulletinProgress=Retrieving Field Desk Draft Bulletins");
		addEnglishTranslation("field:ChunkProgressStatusMessage=Download Progress");
		addEnglishTranslation("field:RetrieveMySealedBulletinSummaries=Retrieving Sealed Bulletin Summaries");
		addEnglishTranslation("field:RetrieveMyDraftBulletinSummaries=Retrieving Draft Bulletin Summaries");
		addEnglishTranslation("field:RetrieveHQSealedBulletinSummaries=Retrieving Field Desk Sealed Bulletin Summaries");
		addEnglishTranslation("field:RetrieveHQDraftBulletinSummaries=Retrieving Field Desk Draft Bulletin Summaries");

		addEnglishTranslation("field:VirtualUserNameDescription=(Enter using regular keyboard)");
		addEnglishTranslation("field:VirtualPasswordDescription=Enter Password using mouse with on-screen keyboard below");
		addEnglishTranslation("field:VirtualKeyboardKeys=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890-+=!@#$%^&*()_,.[]{}<>\\/?|;:~");
		addEnglishTranslation("field:VirtualKeyboardSpace=Space");
		addEnglishTranslation("field:VirtualKeyboardBackSpace=Back Space");

		addEnglishTranslation("field:confirmquestion=Are you sure you want to do this?");
		addEnglishTranslation("field:confirmsendcause=You have chosen to send a completed bulletin to a Martus server.  ");
		addEnglishTranslation("field:confirmsendeffect=This will permanently seal the bulletin from future edits.  ");
		addEnglishTranslation("field:confirmDiscardDraftBulletincause=You have chosen to delete a draft bulletin from the Discarded Bulletins folder.");
		addEnglishTranslation("field:confirmDiscardDraftBulletineffect=This will permanently delete the bulletin from this computer.");
		addEnglishTranslation("field:confirmDiscardSealedBulletincause=You have chosen to delete a sealed bulletin from the Discarded Bulletins folder.");
		addEnglishTranslation("field:confirmDiscardSealedBulletineffect=If this bulletin has already been sent to a server, it will remain on the server. This action will only delete it from this computer.");
		addEnglishTranslation("field:confirmdeletefoldercause=You have chosen to permanently delete a folder.  ");
		addEnglishTranslation("field:confirmdeletefoldereffect=Any bulletins in the folder will be moved to Discarded Bulletins.  ");
		addEnglishTranslation("field:confirmretrievecause=You have chosen to retrieve all bulletins from the Martus server.  ");
		addEnglishTranslation("field:confirmretrieveeffect=This will restore all the discarded bulletins that were sent to the server.  ");
		addEnglishTranslation("field:confirmRemoveAttachmentcause=You have chosen to remove the selected attachments from this bulletin.");
		addEnglishTranslation("field:confirmRemoveAttachmenteffect=The selected attachments will be permanently removed from this bulletin.");
		addEnglishTranslation("field:confirmOverWriteExistingFilecause=This file already exists, do you wish to overwrite this file?");
		addEnglishTranslation("field:confirmOverWriteExistingFileeffect=The selected attachment will replace the file on your hard drive.");
		addEnglishTranslation("field:confirmCancelBulletinEditcause=You have chosen to cancel editing this bulletin.");
		addEnglishTranslation("field:confirmCancelBulletinEditeffect=Any changes you have made to this bulletin will be discarded.");
		addEnglishTranslation("field:confirmSetImportPublicKeycause=You have chosen to allow this client the ability to view your public and PRIVATE data.");
		addEnglishTranslation("field:confirmSetImportPublicKeyeffect=By clicking on Yes you are authorizing this client to view all portions of your bulletins.");
		addEnglishTranslation("field:confirmWarningSwitchToNormalKeyboardcause=Warning! Using the regular keyboard to enter your password greatly reduces the security of the Martus system, and could make it easier for an attacker to view your private data.");
		addEnglishTranslation("field:confirmWarningSwitchToNormalKeyboardeffect=If this is a headquarters computer, it is especially important to use the on-screen keyboard, because an attacker could gain access to all the private data that you are authorized to view.");
		addEnglishTranslation("field:confirmDeleteDiscardedBulletinWithCopiescause=You have chosen to delete a bulletin from the Discarded Bulletins folder.");
		addEnglishTranslation("field:confirmDeleteDiscardedBulletinWithCopieseffect=Deleting this copy will not remove the other copies of this bulletin from these folders:");
		addEnglishTranslation("field:confirmDeleteDiscardedDraftBulletinWithOutboxCopycause=You have chosen to delete a bulletin from the Discarded Bulletins folder.");
		addEnglishTranslation("field:confirmDeleteDiscardedDraftBulletinWithOutboxCopyeffect=This draft bulletin has not been backed up to the server since it was last modified. Discarding this copy will prevent the latest changes from being backed up to the server.");
		addEnglishTranslation("field:confirmClearHQInformationcause=You have chosen to remove your headquarter's account.  Any existing saved bulletins will still be visible by the old headquarters. Any sealed bulletins CANNOT be reset to be non-viewable by the headquarters. Draft bulletins must be re-saved after revoking the headquarters.");
		addEnglishTranslation("field:confirmClearHQInformationeffect=By clicking on Yes any future saved or sealed bulletin will no longer be accessible by your headquarters.");
		addEnglishTranslation("field:confirmCloneMySealedAsDraftcause=You have chosen to edit one of your sealed bulletins, but sealed bulletins cannot be modified.");
		addEnglishTranslation("field:confirmCloneMySealedAsDrafteffect=Clicking on Yes will create a new bulletin that contains all the same information, and the original sealed bulletin will remain unchanged.");
		addEnglishTranslation("field:confirmCloneBulletinAsMinecause=You have chosen to edit a bulletin that was created by someone else.");
		addEnglishTranslation("field:confirmCloneBulletinAsMineeffect=Clicking on Yes will create a new bulletin that contains a copy of all the same information. You will be the official author of this new bulletin, and any private data in it will only be visible by you (and your headquarters, if you have one). The original bulletin will remain unchanged.");
		addEnglishTranslation("field:confirmPrinterWarningcause=Since you have changed from the default print tray your print out may be incorrect, if you also changed the size of paper used.  You must first select the paper tray and then select the paper size in that order, for both to get set correctly.  If you only wanted to change the paper tray then disregard this message and select 'No'.");
		addEnglishTranslation("field:confirmPrinterWarningeffect=Clicking on Yes will bring back the Printer Dialog so you can reselect your default paper tray and size.  Clicking on 'No' will print the document.");

		addEnglishTranslation("field:notifyDropNotAllowedcause=This bulletin cannot be moved to that folder. This may be because of its Draft/Sealed status, or its author.");
		addEnglishTranslation("field:notifyDropErrorcause=An unexpected error occured while moving this bulletin. The file may be damaged.");
		addEnglishTranslation("field:notifyretrieveworkedcause=All of the selected bulletins were successfully retrieved from the server");
		addEnglishTranslation("field:notifyretrievefailedcause=Error: Unable to retrieve bulletins from the server");
		addEnglishTranslation("field:notifyretrievenothingcause=No bulletins were selected");
		addEnglishTranslation("field:notifyconfignoservercause=The selected server is not responding. Before you choose a server, you must be connected to the internet, and that server must be available.");
		addEnglishTranslation("field:notifyretrievenoservercause=The current server is not responding");
		addEnglishTranslation("field:notifypasswordsdontmatchcause=You must enter the same password twice");
		addEnglishTranslation("field:notifyusernamessdontmatchcause=You must enter the same username twice");
		addEnglishTranslation("field:notifyUserNameBlankcause=User Name must not be blank");
		addEnglishTranslation("field:notifyPasswordInvalidcause=Not a valid password, passwords must be at least 8 characters long");
		addEnglishTranslation("field:notifyPasswordMatchesUserNamecause=Your password can not be your username");
		addEnglishTranslation("field:notifyincorrectsignincause=Username or Password incorrect");
		addEnglishTranslation("field:notifyuploadremindercause=Please Note: There are bulletins in your outbox that have not been sent to a server.");
		addEnglishTranslation("field:notifyuploadrejectedcause=The current Martus Server has refused to accept a bulletin");
		addEnglishTranslation("field:notifycorruptconfiginfocause=The configuration file may be corrupted");
		addEnglishTranslation("field:notifyservercodewrongcause=The Server Public Code does not match the one you entered.");
		addEnglishTranslation("field:notifyserverinfoinvalidcause=The Server has responded with invalid account information.");
		addEnglishTranslation("field:notifyserverokcause=The Server has been selected.");
		addEnglishTranslation("field:notifymagicwordokcause=The Server has accepted your request for permission to upload bulletins.");
		addEnglishTranslation("field:notifymagicwordrejectedcause=The Server has rejected your request. The magic word is probably not correct.");
		addEnglishTranslation("field:notifyRewriteKeyPairFailedcause=An error occured.  Unable to change user name or password.  You may need to restore your backup key pair file.");
		addEnglishTranslation("field:notifyRewriteKeyPairWorkedcause=Successfully saved your new username and password.\n\nWe strongly recommend that you back up your keypair.dat file at this time!");
		addEnglishTranslation("field:notifyUnableToSaveAttachmentcause=Unable to save the selected attachment for some reason.  Try saving it to a different file.");
		addEnglishTranslation("field:notifySearchFailedcause=Sorry, no bulletins were found.");
		addEnglishTranslation("field:notifySearchFoundcause=Number of bulletins found = ");
		addEnglishTranslation("field:notifyServerErrorcause=Server Error, the server may be down, please try again later");
		addEnglishTranslation("field:notifyFoundOrphanscause=One or more bulletins were not in any folder. These lost bulletins have been placed into the Recovered Bulletins folder.");
		addEnglishTranslation("field:notifyFoundDamagedBulletinscause=One or more bulletins were severely damaged, and cannot be displayed. If these bulletins were backed up to a server, you may be able to retrieve undamaged copies from there.");
		addEnglishTranslation("field:notifyErrorSavingStatecause=Unable to save current screen layout.");
		addEnglishTranslation("field:notifyExportPublicInfocause=The following file has been exported.");
		addEnglishTranslation("field:notifyPublicInfoFileErrorcause=The file does not contain valid public information.");
		addEnglishTranslation("field:notifyAccountCodeWrongcause=The Public Code does not match the one you entered.");
		addEnglishTranslation("field:notifyErrorSavingConfigcause=Unable to save configuration file.");
		addEnglishTranslation("field:notifyWarningBetaCopycause=This is a beta version of the Martus software.  It's being shared now to review the software during testing because it may contain bugs.  There may be undiscovered security bugs, and therefore people should use caution and judgment when writing into the private data fields names or other information that could put individuals in danger if it were discovered by unintended parties.");
		addEnglishTranslation("field:notifyBetaExpiredcause=This Beta Test version of Martus is very old. We strongly recommend that you upgrade to a newer version, to ensure that you have the latest security and other features. Please contact the person or organization who gave you this copy of Martus, or send an email to beta@martus.org");
		addEnglishTranslation("field:notifyAuthenticateServerFailedcause=Martus could not authenticate the server. The server may have been compromised.  Please verify your server configuration and contact the server operator.");
		addEnglishTranslation("field:notifyWelcomeToMartuscause=Welcome to the Martus system. Before you can begin working, you must create an account by choosing a username and passphrase. You can choose any username you wish, and you can choose any reasonably secure passphrase. We recommend that your passphrase be fairly long and complex, to prevent other people from guessing it. Anyone who guesses or learns your username and passphrase will be able to view your bulletins (including the private portions), and will be able to create bulletins that everyone will believe were created by you. \n\nEvery time you start Martus after this, you will need to enter your username and pasphrase in order to access your bulletins. At certain times, you will also be prompted to re-enter your username and passphrase within Martus to prevent someone else from using the system after you have logged in.\n\nIf you forget your username or passphrase, you will not be able to retrieve your data, so please remember them or write them down and store them in a very secure location. Your username and passphrase are not stored or backed up anywhere, and nobody can 'reset' them for you.");
		addEnglishTranslation("field:notifyUnexpectedErrorcause=An unexpected error has occured. Please report this problem to www.martus.org.");

		addEnglishTranslation("field:inputservernameentry=Enter the name or IP address of the server:");
		addEnglishTranslation("field:inputserverpubliccodeentry=Enter the Public Identification Code for this server:");
		addEnglishTranslation("field:inputsearchentry=Search for:");
		addEnglishTranslation("field:inputservermagicwordentry=If you want to request permission to upload to this server, enter the 'magic word' now:");
		addEnglishTranslation("field:inputImportPublicCodeentry=Enter the Public Identification Code for this account:");
		
		addEnglishTranslation("field:username=Username");
		addEnglishTranslation("field:password1=Password");
		addEnglishTranslation("field:password2=(same password again)");
		addEnglishTranslation("field:organization=Organization");
		addEnglishTranslation("field:email=Email Address");
		addEnglishTranslation("field:webpage=Web Page");
		addEnglishTranslation("field:phone=Phone Number");
		addEnglishTranslation("field:address=Mailing Address");
		addEnglishTranslation("field:attachments=Attachments");
		addEnglishTranslation("field:username=Username");
		addEnglishTranslation("field:password=Password");
		addEnglishTranslation("field:securityServerConfigValidate=For security reasons, we must validate your username and password.");
		addEnglishTranslation("field:RetypeUserNameAndPassword=Please retype your username and password for verification.");
		addEnglishTranslation("field:CreateNewUserNamePassword=Please enter your new username and password.");
		addEnglishTranslation("field:HelpOnCreatingNewPassword=When choosing a password, it is important not to use a password that is easy to guess like names, important dates of events or simple words.  Try adding numbers to random letters and making the password long.  Remember your password, but do not share it.  No one else has access to the password if you forget it, so if you write it down, put it in a safe place.");
		addEnglishTranslation("field:timedout1=For security reasons, you must sign into Martus again.");
		addEnglishTranslation("field:timedout2=Any unsaved bulletins will be lost unless you sign in and save them.");
		addEnglishTranslation("field:defaultFolderName=New Folder");
		addEnglishTranslation("field:searchrules=When searching for bulletins you can add the key words 'or', 'and' between multiple words.");
		addEnglishTranslation("field:AccountInfoUserName=User Name: ");
		addEnglishTranslation("field:AccountInfoPublicKey=Public Key:");
		addEnglishTranslation("field:AccountInfoPublicCode=Public Code:");
		addEnglishTranslation("field:NameOfExportedFile=Please enter a name for the file you wish to export.");
		addEnglishTranslation("field:NameOfExportedFile=Please enter a name for the file you wish to export.");

		addEnglishTranslation("folder:%RetrievedMyBulletin=Retrieved Bulletins");
		addEnglishTranslation("folder:%RetrievedFieldOfficeBulletin=Field Desk Bulletins");

		addEnglishTranslation("month:jan=Jan");
		addEnglishTranslation("month:feb=Feb");
		addEnglishTranslation("month:mar=Mar");
		addEnglishTranslation("month:apr=Apr");
		addEnglishTranslation("month:may=May");
		addEnglishTranslation("month:jun=Jun");
		addEnglishTranslation("month:jul=Jul");
		addEnglishTranslation("month:aug=Aug");
		addEnglishTranslation("month:sep=Sep");
		addEnglishTranslation("month:oct=Oct");
		addEnglishTranslation("month:nov=Nov");
		addEnglishTranslation("month:dec=Dec");

		addEnglishTranslation("message:connected=Successfully connected to server");
		addEnglishTranslation("message:noconnect=ERROR: Unable to connect to server");

		addEnglishTranslation("status:draft=Draft");
		addEnglishTranslation("status:sealed=Sealed");

		addEnglishTranslation("keyword:and=and");
		addEnglishTranslation("keyword:or=or");
	}

	public static ChoiceItem DMY_SLASH = new ChoiceItem("dd/MM/yyyy", "dd/mm/yyyy");
	private static ChoiceItem MDY_SLASH = new ChoiceItem("MM/dd/yyyy", "mm/dd/yyyy");
	private static ChoiceItem DMY_DOT = new ChoiceItem("dd.MM.yyyy", "dd.mm.yyyy");

	private static final String ENGLISH = "en";

	private Map languageTranslationsMap;
}
