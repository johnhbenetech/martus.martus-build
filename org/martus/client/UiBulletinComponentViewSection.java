package org.martus.client;

import javax.swing.JComponent;

import org.martus.common.AttachmentProxy;

public class UiBulletinComponentViewSection extends UiBulletinComponentSection 
{

	public UiBulletinComponentViewSection(UiBulletinComponent bulletinComponentToUse, UiMainWindow ownerToUse, MartusApp appToUse, boolean encrypted) 
	{
		super(appToUse, encrypted);
		app = appToUse;
		owner = ownerToUse;
		bulletinComponent = bulletinComponentToUse;
	}

	public UiField createDateField()
	{
		return new UiDateViewer(app);
	}

	public UiField createNormalField()
	{
		return new UiNormalTextViewer(app);
	}

	public UiField createMultilineField()
	{
		return new UiMultilineViewer(app);
	}

	public UiField createChoiceField(ChoiceItem[] choices)
	{
		return new UiChoiceViewer(choices);
	}
	
	public JComponent createAttachmentTable()
	{
		if(attachmentViewer == null)
			attachmentViewer = new UiAttachmentViewer(owner, bulletinComponent);

		return attachmentViewer;
	}
	
	public void addAttachment(AttachmentProxy a)
	{
		attachmentViewer.addAttachment(a);
	}
	
	public void clearAttachments()
	{
		attachmentViewer.clearAttachments();
	}

	UiAttachmentViewer attachmentViewer;
	MartusApp app;
	UiMainWindow owner;
	UiBulletinComponent bulletinComponent;
}
