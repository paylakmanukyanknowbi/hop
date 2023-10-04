/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.pipeline.transforms.redshift.bulkloader;

import org.apache.commons.lang.StringUtils;
import org.apache.hop.core.Const;
import org.apache.hop.core.DbCache;
import org.apache.hop.core.Props;
import org.apache.hop.core.SourceToTargetMapping;
import org.apache.hop.core.SqlStatement;
import org.apache.hop.core.database.Database;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.util.StringUtil;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransformMeta;
import org.apache.hop.pipeline.transform.ITransformDialog;
import org.apache.hop.pipeline.transform.ITransformMeta;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.database.dialog.DatabaseExplorerDialog;
import org.apache.hop.ui.core.database.dialog.SqlEditor;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.dialog.EnterMappingDialog;
import org.apache.hop.ui.core.dialog.ErrorDialog;
import org.apache.hop.ui.core.widget.ColumnInfo;
import org.apache.hop.ui.core.widget.ComboVar;
import org.apache.hop.ui.core.widget.MetaSelectionLine;
import org.apache.hop.ui.core.widget.TableView;
import org.apache.hop.ui.core.widget.TextVar;
import org.apache.hop.ui.pipeline.transform.BaseTransformDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedshiftBulkLoaderDialog extends BaseTransformDialog implements ITransformDialog {

  private static final Class<?> PKG =
      RedshiftBulkLoaderMeta.class; // for i18n purposes, needed by Translator2!!

  private MetaSelectionLine<DatabaseMeta> wConnection;

  private Button wTruncate;

  private Button wOnlyWhenHaveRows;

  private TextVar wSchema;

  private TextVar wTable;

  private Button wStreamToS3Csv;

  private ComboVar wLoadFromExistingFileFormat;

  private TextVar wCopyFromFilename;

  private Button wSpecifyFields;

  private TableView wFields;

  private Button wGetFields;

  private Button wDoMapping;

  private RedshiftBulkLoaderMeta input;

  private Map<String, Integer> inputFields;

  private ColumnInfo[] ciFields;

  /** List of ColumnInfo that should have the field names of the selected database table */
  private List<ColumnInfo> tableFieldColumns = new ArrayList<>();

  /** Constructor. */
  public RedshiftBulkLoaderDialog(
      Shell parent, IVariables variables, Object in, PipelineMeta pipelineMeta, String sname) {
    super(parent, variables, (BaseTransformMeta) in, pipelineMeta, sname);
    input = (RedshiftBulkLoaderMeta) in;
    inputFields = new HashMap<>();
  }

  /** Open the dialog. */
  public String open() {
    FormData fdDoMapping;
    FormData fdGetFields;
    Label wlFields;
    FormData fdSpecifyFields;
    Label wlSpecifyFields;
    FormData fdbTable;
    FormData fdlTable;
    Button wbTable;
    FormData fdSchema;
    FormData fdlSchema;
    Label wlSchema;
    FormData fdMainComp;
    CTabItem wFieldsTab;
    CTabItem wMainTab;
    FormData fdTabFolder;
    CTabFolder wTabFolder;
    Label wlTruncate;
    Shell parent = getParent();

    shell = new Shell(parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN);
    PropsUi.setLook(shell);
    setShellImage(shell, input);

    ModifyListener lsMod = e -> input.setChanged();
    FocusListener lsFocusLost =
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent arg0) {
            setTableFieldCombo();
          }
        };
    backupChanged = input.hasChanged();

    int middle = props.getMiddlePct();
    int margin = Const.MARGIN;

    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = Const.FORM_MARGIN;
    formLayout.marginHeight = Const.FORM_MARGIN;

    shell.setLayout(formLayout);
    shell.setText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.DialogTitle"));

    // TransformName line
    wlTransformName = new Label(shell, SWT.RIGHT);
    wlTransformName.setText(BaseMessages.getString("System.Label.TransformName"));
    PropsUi.setLook(wlTransformName);
    fdlTransformName = new FormData();
    fdlTransformName.left = new FormAttachment(0, 0);
    fdlTransformName.right = new FormAttachment(middle, -margin);
    fdlTransformName.top = new FormAttachment(0, margin*2);
    wlTransformName.setLayoutData(fdlTransformName);
    wTransformName = new Text(shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    wTransformName.setText(transformName);
    PropsUi.setLook(wTransformName);
    wTransformName.addModifyListener(lsMod);
    fdTransformName = new FormData();
    fdTransformName.left = new FormAttachment(middle, 0);
    fdTransformName.top = new FormAttachment(0, margin*2);
    fdTransformName.right = new FormAttachment(100, 0);
    wTransformName.setLayoutData(fdTransformName);

    // Connection line
    DatabaseMeta dbm = pipelineMeta.findDatabase(input.getConnection(), variables);
    wConnection = addConnectionLine(shell, wTransformName, input.getDatabaseMeta(), null);
    if (input.getDatabaseMeta() == null && pipelineMeta.nrDatabases() == 1) {
      wConnection.select(0);
    }
    wConnection.addModifyListener(lsMod);
    wConnection.addModifyListener(event -> setFlags());

    // Schema line...
    wlSchema = new Label(shell, SWT.RIGHT);
    wlSchema.setText(
        BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.TargetSchema.Label")); // $NON-NLS-1$
    PropsUi.setLook(wlSchema);
    fdlSchema = new FormData();
    fdlSchema.left = new FormAttachment(0, 0);
    fdlSchema.right = new FormAttachment(middle, -margin);
    fdlSchema.top = new FormAttachment(wConnection, margin * 2);
    wlSchema.setLayoutData(fdlSchema);

    wSchema = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wSchema);
    wSchema.addModifyListener(lsMod);
    wSchema.addFocusListener(lsFocusLost);
    fdSchema = new FormData();
    fdSchema.left = new FormAttachment(middle, 0);
    fdSchema.top = new FormAttachment(wConnection, margin * 2);
    fdSchema.right = new FormAttachment(100, 0);
    wSchema.setLayoutData(fdSchema);

    // Table line...
    Label wlTable = new Label(shell, SWT.RIGHT);
    wlTable.setText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.TargetTable.Label"));
    PropsUi.setLook(wlTable);
    fdlTable = new FormData();
    fdlTable.left = new FormAttachment(0, 0);
    fdlTable.right = new FormAttachment(middle, -margin);
    fdlTable.top = new FormAttachment(wSchema, margin);
    wlTable.setLayoutData(fdlTable);

    wbTable = new Button(shell, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbTable);
    wbTable.setText(BaseMessages.getString("System.Button.Browse"));
    fdbTable = new FormData();
    fdbTable.right = new FormAttachment(100, 0);
    fdbTable.top = new FormAttachment(wSchema, margin);
    wbTable.setLayoutData(fdbTable);

    wTable = new TextVar(variables, shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wTable);
    wTable.addModifyListener(lsMod);
    wTable.addFocusListener(lsFocusLost);
    FormData fdTable = new FormData();
    fdTable.top = new FormAttachment(wSchema, margin);
    fdTable.left = new FormAttachment(middle, 0);
    fdTable.right = new FormAttachment(wbTable, -margin);
    wTable.setLayoutData(fdTable);

    SelectionAdapter lsSelMod =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent arg0) {
            input.setChanged();
          }
        };

    // Truncate table
    wlTruncate = new Label(shell, SWT.RIGHT);
    wlTruncate.setText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.TruncateTable.Label"));
    PropsUi.setLook(wlTruncate);
    FormData fdlTruncate = new FormData();
    fdlTruncate.left = new FormAttachment(0, 0);
    fdlTruncate.top = new FormAttachment(wTable, margin);
    fdlTruncate.right = new FormAttachment(middle, -margin);
    wlTruncate.setLayoutData(fdlTruncate);
    wTruncate = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wTruncate);
    FormData fdTruncate = new FormData();
    fdTruncate.left = new FormAttachment(middle, 0);
    fdTruncate.top = new FormAttachment(wlTruncate, 0, SWT.CENTER);
    fdTruncate.right = new FormAttachment(100, 0);
    wTruncate.setLayoutData(fdTruncate);
    SelectionAdapter lsTruncMod =
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent arg0) {
            input.setChanged();
          }
        };
    wTruncate.addSelectionListener(lsTruncMod);
    wTruncate.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            setFlags();
          }
        });

    // Truncate only when have rows
    Label wlOnlyWhenHaveRows = new Label(shell, SWT.RIGHT);
    wlOnlyWhenHaveRows.setText(
        BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.OnlyWhenHaveRows.Label"));
    PropsUi.setLook(wlOnlyWhenHaveRows);
    FormData fdlOnlyWhenHaveRows = new FormData();
    fdlOnlyWhenHaveRows.left = new FormAttachment(0, 0);
    fdlOnlyWhenHaveRows.top = new FormAttachment(wTruncate, margin);
    fdlOnlyWhenHaveRows.right = new FormAttachment(middle, -margin);
    wlOnlyWhenHaveRows.setLayoutData(fdlOnlyWhenHaveRows);
    wOnlyWhenHaveRows = new Button(shell, SWT.CHECK);
    wOnlyWhenHaveRows.setToolTipText(
        BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.OnlyWhenHaveRows.Tooltip"));
    PropsUi.setLook(wOnlyWhenHaveRows);
    FormData fdTruncateWhenHaveRows = new FormData();
    fdTruncateWhenHaveRows.left = new FormAttachment(middle, 0);
    fdTruncateWhenHaveRows.top = new FormAttachment(wlOnlyWhenHaveRows, 0, SWT.CENTER);
    fdTruncateWhenHaveRows.right = new FormAttachment(100, 0);
    wOnlyWhenHaveRows.setLayoutData(fdTruncateWhenHaveRows);
    wOnlyWhenHaveRows.addSelectionListener(lsSelMod);

    // Specify fields
    wlSpecifyFields = new Label(shell, SWT.RIGHT);
    wlSpecifyFields.setText(
        BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.SpecifyFields.Label"));
    PropsUi.setLook(wlSpecifyFields);
    FormData fdlSpecifyFields = new FormData();
    fdlSpecifyFields.left = new FormAttachment(0, 0);
    fdlSpecifyFields.top = new FormAttachment(wOnlyWhenHaveRows, margin);
    fdlSpecifyFields.right = new FormAttachment(middle, -margin);
    wlSpecifyFields.setLayoutData(fdlSpecifyFields);
    wSpecifyFields = new Button(shell, SWT.CHECK);
    PropsUi.setLook(wSpecifyFields);
    fdSpecifyFields = new FormData();
    fdSpecifyFields.left = new FormAttachment(middle, 0);
    fdSpecifyFields.top = new FormAttachment(wlSpecifyFields, 0, SWT.CENTER);
    fdSpecifyFields.right = new FormAttachment(100, 0);
    wSpecifyFields.setLayoutData(fdSpecifyFields);
    wSpecifyFields.addSelectionListener(lsSelMod);

    // If the flag is off, gray out the fields tab e.g.
    wSpecifyFields.addSelectionListener(
        new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent arg0) {
            setFlags();
          }
        });

    wTabFolder = new CTabFolder(shell, SWT.BORDER);
    PropsUi.setLook(wTabFolder, Props.WIDGET_STYLE_TAB);

    // ////////////////////////
    // START OF KEY TAB ///
    // /
    wMainTab = new CTabItem(wTabFolder, SWT.NONE);
    wMainTab.setText(
        BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.MainTab.CTabItem")); // $NON-NLS-1$

    FormLayout mainLayout = new FormLayout();
    mainLayout.marginWidth = 3;
    mainLayout.marginHeight = 3;

    Composite wMainComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wMainComp);
    wMainComp.setLayout(mainLayout);

    fdMainComp = new FormData();
    fdMainComp.left = new FormAttachment(0, 0);
    fdMainComp.top = new FormAttachment(0, 0);
    fdMainComp.right = new FormAttachment(100, 0);
    fdMainComp.bottom = new FormAttachment(100, 0);
    wMainComp.setLayoutData(fdMainComp);

    Label wlStreamToS3Csv = new Label(wMainComp, SWT.RIGHT);
    wlStreamToS3Csv.setText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.StreamCsvToS3.Label"));
    wlStreamToS3Csv.setToolTipText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.StreamCsvToS3.ToolTip"));
    PropsUi.setLook(wlStreamToS3Csv);
    FormData fdlStreamToS3Csv = new FormData();
    fdlStreamToS3Csv.top = new FormAttachment(0, margin*2);
    fdlStreamToS3Csv.left = new FormAttachment(0, 0);
    fdlStreamToS3Csv.right = new FormAttachment(middle, -margin);
    wlStreamToS3Csv.setLayoutData(fdlStreamToS3Csv);

    wStreamToS3Csv = new Button(wMainComp, SWT.CHECK);
    wStreamToS3Csv.setToolTipText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.StreamCsvToS3.ToolTip"));
    PropsUi.setLook(wStreamToS3Csv);
    FormData fdStreamToS3Csv = new FormData();
    fdStreamToS3Csv.top = new FormAttachment(0, margin*2);
    fdStreamToS3Csv.left = new FormAttachment(middle, 0);
    fdStreamToS3Csv.right = new FormAttachment(100, 0);
    wStreamToS3Csv.setLayoutData(fdStreamToS3Csv);
//    wStreamToS3Csv.addSelectionListener();
    wStreamToS3Csv.setSelection(true);
    Control lastControl = wStreamToS3Csv;

    wStreamToS3Csv.addSelectionListener(
            new SelectionAdapter() {
              @Override
              public void widgetSelected(SelectionEvent e) {
                if(wStreamToS3Csv.getSelection()){
                  wLoadFromExistingFileFormat.setText("");
                }
                wLoadFromExistingFileFormat.setEnabled(!wStreamToS3Csv.getSelection());
              }
            }
    );

    Label wlLoadFromExistingFile = new Label(wMainComp, SWT.RIGHT);
    wlLoadFromExistingFile.setText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.LoadFromExistingFile.Label"));
    wlLoadFromExistingFile.setToolTipText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.LoadFromExistingFile.Tooltip"));
    PropsUi.setLook(wlLoadFromExistingFile);
    FormData fdlLoadFromExistingFile = new FormData();
    fdlLoadFromExistingFile.top = new FormAttachment(lastControl, margin*2);
    fdlLoadFromExistingFile.left = new FormAttachment(0, 0);
    fdlLoadFromExistingFile.right = new FormAttachment(middle, -margin);
    wlLoadFromExistingFile.setLayoutData(fdlLoadFromExistingFile);

    wLoadFromExistingFileFormat = new ComboVar(variables, wMainComp, SWT.SINGLE | SWT.READ_ONLY | SWT.BORDER);
    FormData fdLoadFromExistingFile = new FormData();
    fdLoadFromExistingFile.top = new FormAttachment(lastControl, margin*2);
    fdLoadFromExistingFile.left = new FormAttachment(middle, 0);
    fdLoadFromExistingFile.right = new FormAttachment(100, 0);
    wLoadFromExistingFileFormat.setLayoutData(fdLoadFromExistingFile);
    String[] fileFormats = {"CSV", "Avro", "Parquet"};
    wLoadFromExistingFileFormat.setItems(fileFormats);
    lastControl = wLoadFromExistingFileFormat;

    Label wlCopyFromFile = new Label(wMainComp, SWT.RIGHT);
    wlCopyFromFile.setText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.CopyFromFile.Label"));
    PropsUi.setLook(wlCopyFromFile);
    FormData fdlCopyFromFile = new FormData();
    fdlCopyFromFile.top = new FormAttachment(lastControl, margin*2);
    fdlCopyFromFile.left = new FormAttachment(0, 0);
    fdlCopyFromFile.right = new FormAttachment(middle, -margin);
    wlCopyFromFile.setLayoutData(fdlCopyFromFile);

    Button wbCopyFromFile = new Button(wMainComp, SWT.PUSH | SWT.CENTER);
    PropsUi.setLook(wbCopyFromFile);
    wbCopyFromFile.setText(BaseMessages.getString("System.Button.Browse"));
    FormData fdbCopyFromFile = new FormData();
    fdbCopyFromFile.top = new FormAttachment(lastControl, margin*2);
    fdbCopyFromFile.right = new FormAttachment(100, 0);
    wbCopyFromFile.setLayoutData(fdbCopyFromFile);

    wCopyFromFilename = new TextVar(variables, wMainComp, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    PropsUi.setLook(wCopyFromFilename);
    wCopyFromFilename.addModifyListener(lsMod);
    wCopyFromFilename.addFocusListener(lsFocusLost);
    FormData fdCopyFromFile = new FormData();
    fdCopyFromFile.top = new FormAttachment(lastControl, margin*2);
    fdCopyFromFile.left = new FormAttachment(middle, 0);
    fdCopyFromFile.right = new FormAttachment(wbCopyFromFile, -margin);
    wCopyFromFilename.setLayoutData(fdCopyFromFile);
    lastControl = wCopyFromFilename;














    wMainComp.layout();
    wMainTab.setControl(wMainComp);

    //
    // Fields tab...
    //
    wFieldsTab = new CTabItem(wTabFolder, SWT.NONE);
    wFieldsTab.setText(
        BaseMessages.getString(
            PKG, "RedshiftBulkLoaderDialog.FieldsTab.CTabItem.Title")); // $NON-NLS-1$

    Composite wFieldsComp = new Composite(wTabFolder, SWT.NONE);
    PropsUi.setLook(wFieldsComp);

    FormLayout fieldsCompLayout = new FormLayout();
    fieldsCompLayout.marginWidth = Const.FORM_MARGIN;
    fieldsCompLayout.marginHeight = Const.FORM_MARGIN;
    wFieldsComp.setLayout(fieldsCompLayout);

    // The fields table
    wlFields = new Label(wFieldsComp, SWT.NONE);
    wlFields.setText(
        BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.InsertFields.Label")); // $NON-NLS-1$
    PropsUi.setLook(wlFields);
    FormData fdlUpIns = new FormData();
    fdlUpIns.left = new FormAttachment(0, 0);
    fdlUpIns.top = new FormAttachment(0, margin);
    wlFields.setLayoutData(fdlUpIns);

    int tableCols = 2;
    int upInsRows =
        (input.getFields() != null && !input.getFields().equals(Collections.emptyList())
            ? input.getFields().size()
            : 1);

    ciFields = new ColumnInfo[tableCols];
    ciFields[0] =
        new ColumnInfo(
            BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.ColumnInfo.TableField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false); //$NON-NLS-1$
    ciFields[1] =
        new ColumnInfo(
            BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.ColumnInfo.StreamField"),
            ColumnInfo.COLUMN_TYPE_CCOMBO,
            new String[] {""},
            false); //$NON-NLS-1$
    tableFieldColumns.add(ciFields[0]);
    wFields =
        new TableView(
            variables,
            wFieldsComp,
            SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL,
            ciFields,
            upInsRows,
            lsMod,
            props);

    wGetFields = new Button(wFieldsComp, SWT.PUSH);
    wGetFields.setText(
        BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.GetFields.Button")); // $NON-NLS-1$
    fdGetFields = new FormData();
    fdGetFields.top = new FormAttachment(wlFields, margin);
    fdGetFields.right = new FormAttachment(100, 0);
    wGetFields.setLayoutData(fdGetFields);

    wDoMapping = new Button(wFieldsComp, SWT.PUSH);
    wDoMapping.setText(
        BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.DoMapping.Button")); // $NON-NLS-1$
    fdDoMapping = new FormData();
    fdDoMapping.top = new FormAttachment(wGetFields, margin);
    fdDoMapping.right = new FormAttachment(100, 0);
    wDoMapping.setLayoutData(fdDoMapping);

    wDoMapping.addListener(
        SWT.Selection,
        new Listener() {
          public void handleEvent(Event arg0) {
            generateMappings();
          }
        });

    FormData fdFields = new FormData();
    fdFields.left = new FormAttachment(0, 0);
    fdFields.top = new FormAttachment(wlFields, margin);
    fdFields.right = new FormAttachment(wDoMapping, -margin);
    fdFields.bottom = new FormAttachment(100, -2 * margin);
    wFields.setLayoutData(fdFields);

    FormData fdFieldsComp = new FormData();
    fdFieldsComp.left = new FormAttachment(0, 0);
    fdFieldsComp.top = new FormAttachment(0, 0);
    fdFieldsComp.right = new FormAttachment(100, 0);
    fdFieldsComp.bottom = new FormAttachment(100, 0);
    wFieldsComp.setLayoutData(fdFieldsComp);

    wFieldsComp.layout();
    wFieldsTab.setControl(wFieldsComp);

    //
    // Search the fields in the background
    //

    final Runnable runnable =
        new Runnable() {
          public void run() {
            TransformMeta transformMeta = pipelineMeta.findTransform(transformName);
            if (transformMeta != null) {
              try {
                IRowMeta row = pipelineMeta.getPrevTransformFields(variables, transformMeta);

                // Remember these fields...
                for (int i = 0; i < row.size(); i++) {
                  inputFields.put(row.getValueMeta(i).getName(), Integer.valueOf(i));
                }

                setComboBoxes();
              } catch (HopException e) {
                log.logError(
                    toString(), BaseMessages.getString("System.Dialog.GetFieldsFailed.Message"));
              }
            }
          }
        };
    new Thread(runnable).start();

    // Some buttons
    wOk = new Button(shell, SWT.PUSH);
    wOk.setText(BaseMessages.getString("System.Button.OK"));
    wCreate = new Button(shell, SWT.PUSH);
    wCreate.setText(BaseMessages.getString("System.Button.SQL"));
    wCancel = new Button(shell, SWT.PUSH);
    wCancel.setText(BaseMessages.getString("System.Button.Cancel"));

    setButtonPositions(new Button[] {wOk, wCancel, wCreate}, margin, null);

    fdTabFolder = new FormData();
    fdTabFolder.left = new FormAttachment(0, 0);
    fdTabFolder.top = new FormAttachment(wlSpecifyFields, margin);
    fdTabFolder.right = new FormAttachment(100, 0);
    fdTabFolder.bottom = new FormAttachment(wOk, -2 * margin);
    wTabFolder.setLayoutData(fdTabFolder);
    wTabFolder.setSelection(0);

    // Add listeners
    wOk.addListener(SWT.Selection, c -> ok());
    wCancel.addListener(SWT.Selection, c -> cancel());
    wCreate.addListener(SWT.Selection, c -> sql());
    wGetFields.addListener(SWT.Selection, c -> get());

    // Set the shell size, based upon previous time...
    setSize();

    getData();
    setTableFieldCombo();
    input.setChanged(backupChanged);

    BaseDialog.defaultShellHandling(shell, c -> ok(), c -> cancel());
    return transformName;
  }

  /**
   * Reads in the fields from the previous transforms and from the ONE next transform and opens an
   * EnterMappingDialog with this information. After the user did the mapping, those information is
   * put into the Select/Rename table.
   */
  private void generateMappings() {

    // Determine the source and target fields...
    //
    IRowMeta sourceFields;
    IRowMeta targetFields;

    try {
      sourceFields = pipelineMeta.getPrevTransformFields(variables, transformMeta);
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              PKG, "RedshiftBulkLoaderDialog.DoMapping.UnableToFindSourceFields.Title"),
          BaseMessages.getString(
              PKG, "RedshiftBulkLoaderDialog.DoMapping.UnableToFindSourceFields.Message"),
          e);
      return;
    }

    // refresh data
    input.setTablename(variables.resolve(wTable.getText()));
    ITransformMeta transformMetaInterface = transformMeta.getTransform();
    try {
      targetFields = transformMetaInterface.getRequiredFields(variables);
    } catch (HopException e) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(
              PKG, "RedshiftBulkLoaderDialog.DoMapping.UnableToFindTargetFields.Title"),
          BaseMessages.getString(
              PKG, "RedshiftBulkLoaderDialog.DoMapping.UnableToFindTargetFields.Message"),
          e);
      return;
    }

    String[] inputNames = new String[sourceFields.size()];
    for (int i = 0; i < sourceFields.size(); i++) {
      IValueMeta value = sourceFields.getValueMeta(i);
      inputNames[i] = value.getName();
    }

    // Create the existing mapping list...
    //
    List<SourceToTargetMapping> mappings = new ArrayList<>();
    StringBuffer missingSourceFields = new StringBuffer();
    StringBuffer missingTargetFields = new StringBuffer();

    int nrFields = wFields.nrNonEmpty();
    for (int i = 0; i < nrFields; i++) {
      TableItem item = wFields.getNonEmpty(i);
      String source = item.getText(2);
      String target = item.getText(1);

      int sourceIndex = sourceFields.indexOfValue(source);
      if (sourceIndex < 0) {
        missingSourceFields.append(Const.CR + "   " + source + " --> " + target);
      }
      int targetIndex = targetFields.indexOfValue(target);
      if (targetIndex < 0) {
        missingTargetFields.append(Const.CR + "   " + source + " --> " + target);
      }
      if (sourceIndex < 0 || targetIndex < 0) {
        continue;
      }

      SourceToTargetMapping mapping = new SourceToTargetMapping(sourceIndex, targetIndex);
      mappings.add(mapping);
    }

    // show a confirm dialog if some missing field was found
    //
    if (missingSourceFields.length() > 0 || missingTargetFields.length() > 0) {

      String message = "";
      if (missingSourceFields.length() > 0) {
        message +=
            BaseMessages.getString(
                    PKG,
                    "RedshiftBulkLoaderDialog.DoMapping.SomeSourceFieldsNotFound",
                    missingSourceFields.toString())
                + Const.CR;
      }
      if (missingTargetFields.length() > 0) {
        message +=
            BaseMessages.getString(
                    PKG,
                    "RedshiftBulkLoaderDialog.DoMapping.SomeTargetFieldsNotFound",
                    missingSourceFields.toString())
                + Const.CR;
      }
      message += Const.CR;
      message +=
          BaseMessages.getString(
                  PKG, "RedshiftBulkLoaderDialog.DoMapping.SomeFieldsNotFoundContinue")
              + Const.CR;
      int answer =
          BaseDialog.openMessageBox(
              shell,
              BaseMessages.getString(
                  PKG, "RedshiftBulkLoaderDialog.DoMapping.SomeFieldsNotFoundTitle"),
              message,
              SWT.ICON_QUESTION | SWT.YES | SWT.NO);
      boolean goOn = (answer & SWT.YES) != 0;
      if (!goOn) {
        return;
      }
    }
    EnterMappingDialog d =
        new EnterMappingDialog(
            RedshiftBulkLoaderDialog.this.shell,
            sourceFields.getFieldNames(),
            targetFields.getFieldNames(),
            mappings);
    mappings = d.open();

    // mappings == null if the user pressed cancel
    //
    if (mappings != null) {
      // Clear and re-populate!
      //
      wFields.table.removeAll();
      wFields.table.setItemCount(mappings.size());
      for (int i = 0; i < mappings.size(); i++) {
        SourceToTargetMapping mapping = (SourceToTargetMapping) mappings.get(i);
        TableItem item = wFields.table.getItem(i);
        item.setText(2, sourceFields.getValueMeta(mapping.getSourcePosition()).getName());
        item.setText(1, targetFields.getValueMeta(mapping.getTargetPosition()).getName());
      }
      wFields.setRowNums();
      wFields.optWidth(true);
    }
  }

  private void setTableFieldCombo() {
    Runnable fieldLoader =
        () -> {
          // clear
          for (int i = 0; i < tableFieldColumns.size(); i++) {
            ColumnInfo colInfo = (ColumnInfo) tableFieldColumns.get(i);
            colInfo.setComboValues(new String[] {});
          }
          if (!StringUtil.isEmpty(wTable.getText())) {
            DatabaseMeta databaseMeta = pipelineMeta.findDatabase(wConnection.getText(), variables);
            if (databaseMeta != null) {
              try (Database db = new Database(loggingObject, variables, databaseMeta)) {
                db.connect();

                String schemaTable =
                    databaseMeta.getQuotedSchemaTableCombination(
                        variables,
                        variables.resolve(wSchema.getText()),
                        variables.resolve(wTable.getText()));
                IRowMeta r = db.getTableFields(schemaTable);
                if (null != r) {
                  String[] fieldNames = r.getFieldNames();
                  if (null != fieldNames) {
                    for (int i = 0; i < tableFieldColumns.size(); i++) {
                      ColumnInfo colInfo = (ColumnInfo) tableFieldColumns.get(i);
                      colInfo.setComboValues(fieldNames);
                    }
                  }
                }
              } catch (Exception e) {
                for (int i = 0; i < tableFieldColumns.size(); i++) {
                  ColumnInfo colInfo = (ColumnInfo) tableFieldColumns.get(i);
                  colInfo.setComboValues(new String[] {});
                }
                // ignore any errors here. drop downs will not be
                // filled, but no problem for the user
              }
            }
          }
        };
    shell.getDisplay().asyncExec(fieldLoader);
  }

  protected void setComboBoxes() {
    // Something was changed in the row.
    //
    final Map<String, Integer> fields = new HashMap<>();

    // Add the currentMeta fields...
    fields.putAll(inputFields);

    Set<String> keySet = fields.keySet();
    List<String> entries = new ArrayList<>(keySet);

    String[] fieldNames = (String[]) entries.toArray(new String[entries.size()]);

    if (PropsUi.getInstance().isSortFieldByName()) {
      Const.sortStrings(fieldNames);
    }
    ciFields[1].setComboValues(fieldNames);
  }

  public void setFlags() {
    boolean specifyFields = wSpecifyFields.getSelection();
    wFields.setEnabled(specifyFields);
    wGetFields.setEnabled(specifyFields);
    wDoMapping.setEnabled(specifyFields);
  }

  /** Copy information from the meta-data input to the dialog fields. */
  public void getData() {
    if(!StringUtils.isEmpty(input.getConnection())) {
      wConnection.setText(input.getConnection());
    }
    if(!StringUtils.isEmpty(input.getSchemaName())) {
      wSchema.setText(input.getSchemaName());
    }
    if(!StringUtils.isEmpty(input.getTableName())) {
      wTable.setText(input.getTableName());
    }
    wStreamToS3Csv.setSelection(input.isStreamToS3Csv());
    if(!StringUtils.isEmpty(input.getLoadFromExistingFileFormat())){
      wLoadFromExistingFileFormat.setText(input.getLoadFromExistingFileFormat());
    }
    if(!StringUtils.isEmpty(input.getCopyFromFilename())){
      wCopyFromFilename.setText(input.getCopyFromFilename());
    }

    wSpecifyFields.setSelection(input.specifyFields());

    for (int i = 0; i < input.getFields().size(); i++) {
      RedshiftBulkLoaderField vbf = input.getFields().get(i);
      TableItem item = wFields.table.getItem(i);
      if (vbf.getDatabaseField() != null) {
        item.setText(1, vbf.getDatabaseField());
      }
      if (vbf.getStreamField() != null) {
        item.setText(2, vbf.getStreamField());
      }
    }

    setFlags();

    wTransformName.selectAll();
  }

  private void cancel() {
    transformName = null;
    input.setChanged(backupChanged);
    dispose();
  }

  private void getInfo(RedshiftBulkLoaderMeta info) {
    if(!StringUtils.isEmpty(wConnection.getText())){
      info.setConnection(wConnection.getText());
    }
    if(!StringUtils.isEmpty(wSchema.getText())){
      info.setSchemaName(wSchema.getText());
    }
    if(!StringUtils.isEmpty(wTable.getText())){
      info.setTablename(wTable.getText());
    }
    info.setTruncateTable(wTruncate.getSelection());
    info.setOnlyWhenHaveRows(wOnlyWhenHaveRows.getSelection());
    info.setStreamToS3Csv(wStreamToS3Csv.getSelection());
    if(!StringUtils.isEmpty(wLoadFromExistingFileFormat.getText())){
      info.setLoadFromExistingFileFormat(wLoadFromExistingFileFormat.getText());
    }
    if(!StringUtils.isEmpty(wCopyFromFilename.getText())){
      info.setCopyFromFilename(wCopyFromFilename.getText());
    }

    info.setSpecifyFields(wSpecifyFields.getSelection());

    int nrRows = wFields.nrNonEmpty();
    info.getFields().clear();

    for (int i = 0; i < nrRows; i++) {
      TableItem item = wFields.getNonEmpty(i);
      RedshiftBulkLoaderField vbf =
          new RedshiftBulkLoaderField(
              Const.NVL(item.getText(1), ""), Const.NVL(item.getText(2), ""));
      info.getFields().add(vbf);
    }
  }

  private void ok() {
    if (StringUtil.isEmpty(wTransformName.getText())) {
      return;
    }

    transformName = wTransformName.getText(); // return value

    getInfo(input);

    if (Utils.isEmpty(input.getConnection())) {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.ConnectionError.DialogMessage"));
      mb.setText(BaseMessages.getString("System.Dialog.Error.Title"));
      mb.open();
      return;
    }

    dispose();
  }

  private void getTableName() {

    String connectionName = wConnection.getText();
    if (StringUtil.isEmpty(connectionName)) {
      return;
    }
    DatabaseMeta databaseMeta = pipelineMeta.findDatabase(connectionName, variables);

    if (databaseMeta != null) {
      log.logDebug(
          toString(),
          BaseMessages.getString(
              PKG, "RedshiftBulkLoaderDialog.Log.LookingAtConnection", databaseMeta.toString()));

      DatabaseExplorerDialog std =
          new DatabaseExplorerDialog(
              shell, SWT.NONE, variables, databaseMeta, pipelineMeta.getDatabases());
      std.setSelectedSchemaAndTable(wSchema.getText(), wTable.getText());
      if (std.open()) {
        wSchema.setText(Const.NVL(std.getSchemaName(), ""));
        wTable.setText(Const.NVL(std.getTableName(), ""));
      }
    } else {
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
      mb.setMessage(
          BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.ConnectionError2.DialogMessage"));
      mb.setText(BaseMessages.getString("System.Dialog.Error.Title"));
      mb.open();
    }
  }

  /** Fill up the fields table with the incoming fields. */
  private void get() {
    try {
      IRowMeta r = pipelineMeta.getPrevTransformFields(variables, transformName);
      if (r != null && !r.isEmpty()) {
        BaseTransformDialog.getFieldsFromPrevious(
            r, wFields, 1, new int[] {1, 2}, new int[] {}, -1, -1, null);
      }
    } catch (HopException ke) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.FailedToGetFields.DialogTitle"),
          BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.FailedToGetFields.DialogMessage"),
          ke); //$NON-NLS-1$ //$NON-NLS-2$
    }
  }

  // Generate code for create table...
  // Conversions done by Database
  //
  private void sql() {
    try {
      RedshiftBulkLoaderMeta info = new RedshiftBulkLoaderMeta();
      DatabaseMeta databaseMeta = pipelineMeta.findDatabase(wConnection.getText(), variables);

      getInfo(info);
      IRowMeta prev = pipelineMeta.getPrevTransformFields(variables, transformName);
      TransformMeta transformMeta = pipelineMeta.findTransform(transformName);

      if (info.specifyFields()) {
        // Only use the fields that were specified.
        IRowMeta prevNew = new RowMeta();

        for (int i = 0; i < info.getFields().size(); i++) {
          RedshiftBulkLoaderField vbf = info.getFields().get(i);
          IValueMeta insValue = prev.searchValueMeta(vbf.getStreamField());
          if (insValue != null) {
            IValueMeta insertValue = insValue.clone();
            insertValue.setName(vbf.getDatabaseField());
            prevNew.addValueMeta(insertValue);
          } else {
            throw new HopTransformException(
                BaseMessages.getString(
                    PKG,
                    "RedshiftBulkLoaderDialog.FailedToFindField.Message",
                    vbf.getStreamField())); // $NON-NLS-1$
          }
        }
        prev = prevNew;
      }

      SqlStatement sql =
          info.getSqlStatements(variables, pipelineMeta, transformMeta, prev, metadataProvider);
      if (!sql.hasError()) {
        if (sql.hasSql()) {
          SqlEditor sqledit =
              new SqlEditor(
                  shell, SWT.NONE, variables, databaseMeta, DbCache.getInstance(), sql.getSql());
          sqledit.open();
        } else {
          MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
          mb.setMessage(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.NoSQL.DialogMessage"));
          mb.setText(BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.NoSQL.DialogTitle"));
          mb.open();
        }
      } else {
        MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
        mb.setMessage(sql.getError());
        mb.setText(BaseMessages.getString("System.Dialog.Error.Title"));
        mb.open();
      }
    } catch (HopException ke) {
      new ErrorDialog(
          shell,
          BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.BuildSQLError.DialogTitle"),
          BaseMessages.getString(PKG, "RedshiftBulkLoaderDialog.BuildSQLError.DialogMessage"),
          ke);
    }
  }

  @Override
  public String toString() {
    return this.getClass().getName();
  }
}
