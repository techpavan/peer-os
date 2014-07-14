package org.safehaus.subutai.ui.commandrunner;

import com.vaadin.data.Property;
import com.vaadin.server.VaadinService;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.JavaScript;
import com.vaadin.ui.TextField;
import org.safehaus.subutai.shared.protocol.FileUtil;

/**
 * Created by daralbaev on 7/9/14.
 */
public class TerminalControl extends CssLayout {
	private TextField textField;
	private String inputPrompt;
	private String username, currentPath, machineName;

	public TerminalControl() {
		username = (String) VaadinService.getCurrentRequest().getWrappedSession().getAttribute("username");
		currentPath = "/";
		machineName = "";
		setId("terminal");

		textField = new TextField();
		textField.setImmediate(true);
		textField.addValueChangeListener(new Property.ValueChangeListener() {
			@Override
			public void valueChange(Property.ValueChangeEvent event) {
				System.out.println(textField.getValue());
			}
		});
		textField.addStyleName("terminal_submit");

//		initCommandPrompt();
		addComponent(textField);

		this.setSizeFull();
	}

	public void initCommandPrompt() {
		JavaScript.getCurrent().execute(FileUtil.getContent("js/termlib.js", this));
		JavaScript.getCurrent().execute(FileUtil.getContent("js/terminal.js", this));

//		setInputPrompt();
	}

	public void setInputPrompt() {
		inputPrompt = String.format("%s@%s:%s>", username, machineName, currentPath);
		JavaScript.getCurrent().execute(FileUtil.getContent("js/terminal.js", this));
	}
}
