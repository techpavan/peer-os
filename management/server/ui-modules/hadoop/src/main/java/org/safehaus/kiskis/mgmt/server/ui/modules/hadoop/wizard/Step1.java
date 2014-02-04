/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.kiskis.mgmt.server.ui.modules.hadoop.wizard;

import com.vaadin.data.Property;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.terminal.Sizeable;
import com.vaadin.ui.*;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;

import java.util.List;

/**
 * @author bahadyr
 */
public class Step1 extends Panel {

    private HadoopWizard parent;
    private ComboBox comboBoxNameNode;
    private ComboBox comboBoxJobTracker;
    private ComboBox comboBoxSecondaryNameNode;

    public Step1(final HadoopWizard hadoopWizard) {
        this.parent = hadoopWizard;

        setCaption("Welcome to Hadoop Cluster Installation");
        setSizeFull();

        VerticalLayout verticalLayout = new VerticalLayout();
        verticalLayout.setSizeFull();
        verticalLayout.setHeight(100, Sizeable.UNITS_PERCENTAGE);
        verticalLayout.setMargin(true);

        GridLayout grid = new GridLayout(6, 15);
        grid.setSpacing(true);
        grid.setSizeFull();

        Panel panel = new Panel();
        Label menu = new Label("Cluster Install Wizard<br>"
                + " 1) <font color=\"#f14c1a\"><strong>Master Configurations</strong></font><br>"
                + " 2) Slave Configurations<br>"
                + " 3) Installation<br>");

        menu.setContentMode(Label.CONTENT_XHTML);
        panel.addComponent(menu);
        grid.addComponent(menu, 0, 0, 1, 14);
        grid.setComponentAlignment(panel, Alignment.TOP_CENTER);

        VerticalLayout verticalLayoutForm = new VerticalLayout();
        verticalLayoutForm.setSizeFull();
        verticalLayout.setSpacing(true);

        final TextField textFieldClusterName = new TextField("Enter your cluster name");
        textFieldClusterName.setInputPrompt("Cluster name");
        textFieldClusterName.setRequired(true);
        textFieldClusterName.setRequiredError("Must have a name");
        verticalLayoutForm.addComponent(textFieldClusterName);

        final TextField textFieldDomainName = new TextField("Enter your domain name");
        textFieldDomainName.setInputPrompt("intra.lan");
        textFieldDomainName.setRequired(true);
        textFieldDomainName.setRequiredError("Must have a name");
        verticalLayoutForm.addComponent(textFieldDomainName);

        Label labelNameNode = new Label("Choose the host that will run Name Node:");
        verticalLayoutForm.addComponent(labelNameNode);

        comboBoxNameNode = new ComboBox("Name Node", getDataSourceMasters());
        comboBoxNameNode.setMultiSelect(false);
        comboBoxNameNode.setImmediate(true);
        comboBoxNameNode.setItemCaptionPropertyId("hostname");
        comboBoxNameNode.addListener(new Property.ValueChangeListener() {
            @Override
            public void valueChange(Property.ValueChangeEvent event) {
                parent.getHadoopInstallation().getConfig().setNameNode((Agent) event.getProperty().getValue());
                comboBoxJobTracker.setContainerDataSource(getDataSourceMasters());
            }
        });
        verticalLayoutForm.addComponent(comboBoxNameNode);

        Label labelJobTracker = new Label("Choose the host that will run Job Tracker:");
        verticalLayoutForm.addComponent(labelJobTracker);

        comboBoxJobTracker = new ComboBox("Job Tracker", getDataSourceMasters());
        comboBoxJobTracker.setMultiSelect(false);
        comboBoxJobTracker.setImmediate(true);
        comboBoxJobTracker.setItemCaptionPropertyId("hostname");
        comboBoxJobTracker.addListener(new Property.ValueChangeListener() {
            @Override
            public void valueChange(Property.ValueChangeEvent event) {
                parent.getHadoopInstallation().getConfig().setJobTracker((Agent) event.getProperty().getValue());
                comboBoxSecondaryNameNode.setContainerDataSource(getDataSourceMasters());
            }
        });
        verticalLayoutForm.addComponent(comboBoxJobTracker);

        Label labelSecondaryNameNode = new Label("Choose the host that will run Secondary Name Node:");
        verticalLayoutForm.addComponent(labelSecondaryNameNode);

        comboBoxSecondaryNameNode = new ComboBox("Secondary Name Node", getDataSourceMasters());
        comboBoxSecondaryNameNode.setMultiSelect(false);
        comboBoxSecondaryNameNode.setImmediate(true);
        comboBoxSecondaryNameNode.setItemCaptionPropertyId("hostname");
        comboBoxSecondaryNameNode.addListener(new Property.ValueChangeListener() {
            @Override
            public void valueChange(Property.ValueChangeEvent event) {
                parent.getHadoopInstallation().getConfig().setsNameNode((Agent) event.getProperty().getValue());
            }
        });
        verticalLayoutForm.addComponent(comboBoxSecondaryNameNode);

        Label labelReplicationFactor = new Label("Specify the replication factor:");
        verticalLayoutForm.addComponent(labelReplicationFactor);

        ComboBox comboBoxReplicationFactor = new ComboBox("Dfs Replication Factor");
        for (int i = 1; i <= 5; i++) {
            comboBoxReplicationFactor.addItem(i);
        }
        comboBoxReplicationFactor.setImmediate(true);
        comboBoxReplicationFactor.addListener(new Property.ValueChangeListener() {
            @Override
            public void valueChange(Property.ValueChangeEvent event) {
                parent.getHadoopInstallation().getConfig().setReplicationFactor((Integer) event.getProperty().getValue());
            }
        });
        verticalLayoutForm.addComponent(comboBoxReplicationFactor);

        grid.addComponent(verticalLayoutForm, 2, 0, 5, 14);
        grid.setComponentAlignment(verticalLayoutForm, Alignment.TOP_CENTER);

        Button next = new Button("Next");
        next.addListener(new Button.ClickListener() {

            @Override
            public void buttonClick(Button.ClickEvent event) {
                parent.getHadoopInstallation().getConfig().setClusterName(textFieldClusterName.getValue().toString());
                parent.getHadoopInstallation().getConfig().setDomainName(textFieldDomainName.getValue().toString());
                hadoopWizard.showNext();

            }
        });

        verticalLayout.addComponent(grid);
        verticalLayout.addComponent(next);

        addComponent(verticalLayout);
    }

    private BeanItemContainer getDataSourceMasters(){
        List<Agent> list = parent.getLxcList();

       /* if(parent.getHadoopInstallation().getConfig().getsNameNode() != null){
            list.remove(parent.getHadoopInstallation().getConfig().getsNameNode());
        }

        if(parent.getHadoopInstallation().getConfig().getNameNode() != null){
            list.remove(parent.getHadoopInstallation().getConfig().getNameNode());
        }

        if(parent.getHadoopInstallation().getConfig().getJobTracker() != null){
            list.remove(parent.getHadoopInstallation().getConfig().getJobTracker());
        }*/

        return new BeanItemContainer<Agent>(Agent.class, list);
    }
}
