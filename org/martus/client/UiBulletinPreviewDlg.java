package org.martus.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;

import org.martus.common.FieldDataPacket;

public class UiBulletinPreviewDlg extends JDialog implements ActionListener
{
	public UiBulletinPreviewDlg(UiMainWindow owner, FieldDataPacket fdp)
	{
		super(owner, owner.getApp().getWindowTitle("BulletinPreview"), true);
		boolean isEncrypted = fdp.isEncrypted();
		getContentPane().setLayout(new ParagraphLayout());

		UiBulletinComponentViewSection view = new UiBulletinComponentViewSection(null, owner, owner.getApp(), isEncrypted);
		String[] standardFieldTags = Bulletin.getStandardFieldNames();
		view.createLabelsAndFields(view, standardFieldTags);
		view.createAttachmentTable();
		view.copyDataFromPacket(fdp);
		view.disableEdits();
		view.attachmentViewer.saveButton.setVisible(false);

		JButton ok = new JButton(owner.getApp().getButtonLabel("ok"));
		ok.addActionListener(this);

		getContentPane().add(new JLabel(" "), ParagraphLayout.NEW_PARAGRAPH);
		getContentPane().add(view, ParagraphLayout.NEW_PARAGRAPH);
		getContentPane().add(new JLabel(" "), ParagraphLayout.NEW_PARAGRAPH);
		getContentPane().add(ok, ParagraphLayout.NEW_PARAGRAPH);
		getContentPane().add(new JLabel(" "), ParagraphLayout.NEW_PARAGRAPH);
		
		getRootPane().setDefaultButton(ok);
		owner.centerDlg(this);
		setResizable(true);
		show();
	}

	public void actionPerformed(ActionEvent ae) 
	{
		dispose();
	}

}
