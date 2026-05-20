package cn.silentcrane.jksviewer;

import cn.silentcrane.jksviewer.model.AliasInfo;
import cn.silentcrane.jksviewer.model.GeneratedAliasRequest;
import cn.silentcrane.jksviewer.service.KeystoreDocument;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class JksViewerApp extends Application {
    private final ObservableList<AliasInfo> aliases = FXCollections.observableArrayList();
    private final ObservableList<String> failedPasswordItems = FXCollections.observableArrayList();
    private final Map<String, LinkedHashSet<String>> failedPasswordsByAlias = new HashMap<>();

    private Stage stage;
    private KeystoreDocument document;
    private TableView<AliasInfo> aliasTable;
    private Label fileNameLabel;
    private Label filePathLabel;
    private Label aliasCountLabel;
    private Label storeTypeLabel;
    private Label statusLabel;
    private Label detailAlias;
    private Label detailType;
    private Label detailSubject;
    private Label detailIssuer;
    private Label detailValidity;
    private Label detailSerial;
    private Label detailAlgorithm;
    private PasswordField aliasPasswordField;
    private TextField visibleAliasPasswordField;
    private Button togglePasswordButton;
    private Button addButton;
    private Button deleteButton;
    private Button saveButton;
    private Button verifyButton;
    private Label passwordFeedbackLabel;
    private ListView<String> failedPasswordList;
    private boolean showingAliasPassword;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(createHeader());
        root.setLeft(createSidebar());
        root.setCenter(createAliasTable());
        root.setRight(createInspector());
        root.setBottom(createStatusBar());

        Scene scene = new Scene(root, 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        primaryStage.setTitle("JKS Viewer");
        primaryStage.setMinWidth(1020);
        primaryStage.setMinHeight(660);
        primaryStage.setScene(scene);
        primaryStage.show();
        updateDocumentSummary();
        setStatus("请选择一个 Android JKS 文件开始。", false);
    }

    private HBox createHeader() {
        Label title = new Label("JKS Viewer");
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Android 签名库可视化管理");
        subtitle.getStyleClass().add("brand-subtitle");

        VBox copy = new VBox(2, title, subtitle);
        copy.setAlignment(Pos.CENTER_LEFT);

        Button openButton = new Button("打开文件");
        openButton.getStyleClass().addAll("primary-button", "toolbar-button");
        openButton.setTooltip(new Tooltip("打开现有 JKS / keystore 文件"));
        openButton.setOnAction(event -> openKeystore());

        Button newButton = new Button("新建");
        newButton.getStyleClass().addAll("quiet-button", "toolbar-button");
        newButton.setTooltip(new Tooltip("创建一个新的空 JKS 文件"));
        newButton.setOnAction(event -> createKeystore());

        saveButton = new Button("保存文件");
        saveButton.getStyleClass().addAll("quiet-button", "toolbar-button");
        saveButton.setTooltip(new Tooltip("保存当前 JKS 文件"));
        saveButton.setDisable(true);
        saveButton.setOnAction(event -> saveKeystore());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(14, copy, spacer, openButton, newButton, saveButton);
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox createSidebar() {
        Label sectionTitle = new Label("当前文件");
        sectionTitle.getStyleClass().add("section-title");

        fileNameLabel = new Label("未打开");
        fileNameLabel.getStyleClass().add("file-name");
        filePathLabel = new Label("打开 JKS 后会在这里显示文件路径");
        filePathLabel.getStyleClass().add("muted-label");
        filePathLabel.setWrapText(true);

        aliasCountLabel = new Label("0");
        aliasCountLabel.getStyleClass().add("metric-value");
        Label metricCaption = new Label("alias 总数");
        metricCaption.getStyleClass().add("metric-caption");
        storeTypeLabel = new Label("格式: -");
        storeTypeLabel.getStyleClass().add("metric-caption");
        VBox metric = new VBox(4, aliasCountLabel, metricCaption, storeTypeLabel);
        metric.getStyleClass().add("metric-card");

        addButton = new Button("新增 alias");
        addButton.getStyleClass().add("primary-button");
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.setDisable(true);
        addButton.setOnAction(event -> showAddAliasDialog());

        deleteButton = new Button("删除 alias");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setMaxWidth(Double.MAX_VALUE);
        deleteButton.setDisable(true);
        deleteButton.setOnAction(event -> deleteSelectedAlias());

        VBox sidebar = new VBox(16, sectionTitle, fileNameLabel, filePathLabel, metric, new Separator(), addButton, deleteButton);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(260);
        return sidebar;
    }

    private StackPane createAliasTable() {
        aliasTable = new TableView<>(aliases);
        aliasTable.getStyleClass().add("alias-table");
        aliasTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        aliasTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        aliasTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            renderAliasDetails(newValue);
            updateActionState();
        });

        TableColumn<AliasInfo, String> aliasColumn = new TableColumn<>("Alias");
        aliasColumn.setCellValueFactory(new PropertyValueFactory<>("alias"));
        aliasColumn.setPrefWidth(210);

        TableColumn<AliasInfo, String> typeColumn = new TableColumn<>("类型");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("kind"));
        typeColumn.setPrefWidth(110);

        TableColumn<AliasInfo, String> subjectColumn = new TableColumn<>("证书主体");
        subjectColumn.setCellValueFactory(new PropertyValueFactory<>("subject"));
        subjectColumn.setPrefWidth(260);

        TableColumn<AliasInfo, String> validityColumn = new TableColumn<>("有效期");
        validityColumn.setCellValueFactory(new PropertyValueFactory<>("validityText"));
        validityColumn.setPrefWidth(210);
        validityColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                int rowIndex = getIndex();
                AliasInfo info = empty || rowIndex < 0 || rowIndex >= getTableView().getItems().size()
                        ? null
                        : getTableView().getItems().get(rowIndex);
                pseudoClassStateChanged(AliasInfo.EXPIRED_PSEUDO_CLASS, info != null && info.isExpired());
            }
        });

        TableColumn<AliasInfo, String> algorithmColumn = new TableColumn<>("算法");
        algorithmColumn.setCellValueFactory(new PropertyValueFactory<>("publicKeyAlgorithm"));
        algorithmColumn.setPrefWidth(100);

        aliasTable.getColumns().setAll(List.of(aliasColumn, typeColumn, subjectColumn, validityColumn, algorithmColumn));
        Label placeholder = new Label("尚未打开密钥库文件");
        placeholder.getStyleClass().add("empty-state");
        aliasTable.setPlaceholder(placeholder);

        StackPane wrap = new StackPane(aliasTable);
        wrap.getStyleClass().add("table-wrap");
        return wrap;
    }

    private VBox createInspector() {
        Label title = new Label("Alias 详情");
        title.getStyleClass().add("section-title");

        detailAlias = detailValue("请选择 alias");
        detailType = detailValue("-");
        detailSubject = detailValue("-");
        detailIssuer = detailValue("-");
        detailValidity = detailValue("-");
        detailSerial = detailValue("-");
        detailAlgorithm = detailValue("-");

        GridPane details = new GridPane();
        details.getStyleClass().add("detail-grid");
        details.setVgap(10);
        details.setHgap(12);
        details.getColumnConstraints().addAll(new ColumnConstraints(72), new ColumnConstraints(260));
        addDetail(details, 0, "名称", detailAlias);
        addDetail(details, 1, "类型", detailType);
        addDetail(details, 2, "主体", detailSubject);
        addDetail(details, 3, "签发者", detailIssuer);
        addDetail(details, 4, "有效期", detailValidity);
        addDetail(details, 5, "序列号", detailSerial);
        addDetail(details, 6, "算法", detailAlgorithm);

        Label testerTitle = new Label("密码验证");
        testerTitle.getStyleClass().add("section-title");
        aliasPasswordField = new PasswordField();
        aliasPasswordField.setPromptText("输入当前 alias 的密码");
        aliasPasswordField.setDisable(true);
        aliasPasswordField.setOnAction(event -> verifySelectedAliasPassword());

        visibleAliasPasswordField = new TextField();
        visibleAliasPasswordField.setPromptText("输入当前 alias 的密码");
        visibleAliasPasswordField.setDisable(true);
        visibleAliasPasswordField.setVisible(false);
        visibleAliasPasswordField.setManaged(false);
        visibleAliasPasswordField.setOnAction(event -> verifySelectedAliasPassword());
        aliasPasswordField.textProperty().bindBidirectional(visibleAliasPasswordField.textProperty());

        StackPane passwordFieldStack = new StackPane(aliasPasswordField, visibleAliasPasswordField);
        HBox.setHgrow(passwordFieldStack, Priority.ALWAYS);

        togglePasswordButton = new Button("显示");
        togglePasswordButton.getStyleClass().add("quiet-button");
        togglePasswordButton.setTooltip(new Tooltip("显示或隐藏当前输入的 alias 密码"));
        togglePasswordButton.setDisable(true);
        togglePasswordButton.setOnAction(event -> toggleAliasPasswordVisibility());

        HBox passwordInputRow = new HBox(8, passwordFieldStack, togglePasswordButton);
        passwordInputRow.getStyleClass().add("password-row");

        verifyButton = new Button("验证密码");
        verifyButton.getStyleClass().add("primary-button");
        verifyButton.setMaxWidth(Double.MAX_VALUE);
        verifyButton.setDisable(true);
        verifyButton.setOnAction(event -> verifySelectedAliasPassword());

        passwordFeedbackLabel = new Label("等待输入 alias 密码");
        passwordFeedbackLabel.getStyleClass().add("password-feedback");
        passwordFeedbackLabel.setWrapText(true);

        Label failedTitle = new Label("已失败的密码");
        failedTitle.getStyleClass().add("section-title");
        failedPasswordList = new ListView<>(failedPasswordItems);
        failedPasswordList.getStyleClass().add("failed-password-list");
        failedPasswordList.setPlaceholder(new Label("暂无失败记录"));
        failedPasswordList.setPrefHeight(118);
        failedPasswordList.setFocusTraversable(false);
        failedPasswordList.setMouseTransparent(true);

        VBox inspector = new VBox(
                16,
                title,
                details,
                new Separator(),
                testerTitle,
                passwordInputRow,
                verifyButton,
                passwordFeedbackLabel,
                failedTitle,
                failedPasswordList
        );
        inspector.getStyleClass().add("inspector");
        inspector.setPrefWidth(340);
        return inspector;
    }

    private HBox createStatusBar() {
        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        HBox status = new HBox(statusLabel);
        status.getStyleClass().add("status-bar");
        status.setAlignment(Pos.CENTER_LEFT);
        return status;
    }

    private Label detailValue(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("detail-value");
        label.setWrapText(true);
        return label;
    }

    private void addDetail(GridPane grid, int row, String name, Label value) {
        Label key = new Label(name);
        key.getStyleClass().add("detail-key");
        grid.add(key, 0, row);
        grid.add(value, 1, row);
    }

    private void openKeystore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("打开 Android JKS");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Android / Java KeyStore", "*.jks", "*.keystore", "*.p12", "*.pfx", "*.bks", "*.bcfks"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        Optional<char[]> password = showPasswordDialog("输入库密码", "请输入 JKS 文件的库密码。", "库密码");
        password.ifPresent(chars -> {
            try {
                clearCurrentDocument();
                document = KeystoreDocument.load(file.toPath(), chars);
                refreshAliases();
                setStatus("已打开 " + file.getName() + "，格式 " + document.storeType() + "。", false);
            } catch (Exception ex) {
                showError("打开失败", userFacingMessage(ex));
                setStatus("打开失败，请确认库密码和文件格式。", true);
            } finally {
                Arrays.fill(chars, '\0');
            }
        });
    }

    private void createKeystore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("新建 JKS 文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JKS 文件", "*.jks"));
        chooser.setInitialFileName("android-signing.jks");
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        Optional<char[]> password = showPasswordDialog("设置库密码", "请设置新 JKS 文件的库密码。", "至少 6 位");
        password.ifPresent(chars -> {
            try {
                if (chars.length < 6) {
                    throw new IllegalArgumentException("库密码至少需要 6 位。");
                }
                KeystoreDocument created = KeystoreDocument.create(file.toPath(), chars);
                created.save();
                clearCurrentDocument();
                document = created;
                refreshAliases();
                setStatus("已创建新的 JKS 文件，格式 " + document.storeType() + "。", false);
            } catch (Exception ex) {
                showError("创建失败", userFacingMessage(ex));
                setStatus("创建失败。", true);
            } finally {
                Arrays.fill(chars, '\0');
            }
        });
    }

    private void saveKeystore() {
        if (document == null) {
            return;
        }
        try {
            document.save();
            setStatus("已保存到 " + document.path().getFileName() + "。", false);
        } catch (Exception ex) {
            showError("保存失败", userFacingMessage(ex));
            setStatus("保存失败。", true);
        }
    }

    private void showAddAliasDialog() {
        if (document == null) {
            return;
        }

        Dialog<GeneratedAliasRequest> dialog = new Dialog<>();
        dialog.setTitle("新增 alias");
        dialog.setHeaderText("生成 Android 签名 alias");
        styleDialog(dialog.getDialogPane());
        dialog.getDialogPane().setMinWidth(520);
        dialog.getDialogPane().setPrefWidth(520);

        ButtonType addType = new ButtonType("生成", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addType, ButtonType.CANCEL);

        TextField aliasField = new TextField();
        aliasField.setPromptText("例如 release");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("alias 密码");
        HBox passwordRow = createRevealablePasswordInput(passwordField);
        TextField commonNameField = new TextField("Android App Signing");
        TextField organizationField = new TextField("Example");
        TextField organizationUnitField = new TextField("Mobile");
        TextField localityField = new TextField("Shanghai");
        TextField stateField = new TextField("Shanghai");
        TextField countryField = new TextField("CN");
        ComboBox<String> keySizeBox = new ComboBox<>(FXCollections.observableArrayList("2048", "3072", "4096"));
        keySizeBox.getSelectionModel().select("2048");
        keySizeBox.setMaxWidth(Double.MAX_VALUE);
        ComboBox<String> validityBox = new ComboBox<>(FXCollections.observableArrayList("10", "25", "30", "50"));
        validityBox.getSelectionModel().select("25");
        validityBox.setMaxWidth(Double.MAX_VALUE);

        GridPane form = new GridPane();
        form.getStyleClass().add("alias-form");
        form.setMinWidth(456);
        form.setPrefWidth(456);
        form.setHgap(12);
        form.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints(96);
        ColumnConstraints inputColumn = new ColumnConstraints(348);
        inputColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labelColumn, inputColumn);
        addField(form, 0, "Alias", aliasField);
        addField(form, 1, "密码", passwordRow);
        addField(form, 2, "通用名 CN", commonNameField);
        addField(form, 3, "组织 O", organizationField);
        addField(form, 4, "部门 OU", organizationUnitField);
        addField(form, 5, "城市 L", localityField);
        addField(form, 6, "省份 ST", stateField);
        addField(form, 7, "国家 C", countryField);
        addField(form, 8, "RSA 位数", keySizeBox);
        addField(form, 9, "有效年限", validityBox);
        dialog.getDialogPane().setContent(form);

        Button addButtonNode = (Button) dialog.getDialogPane().lookupButton(addType);
        addButtonNode.getStyleClass().add("primary-button");
        addButtonNode.disableProperty().bind(Bindings.createBooleanBinding(
                () -> aliasField.getText().isBlank() || passwordField.getText().length() < 6,
                aliasField.textProperty(),
                passwordField.textProperty()
        ));

        dialog.setResultConverter(button -> {
            if (button != addType) {
                return null;
            }
            return new GeneratedAliasRequest(
                    aliasField.getText().trim(),
                    passwordField.getText().toCharArray(),
                    commonNameField.getText().trim(),
                    organizationField.getText().trim(),
                    organizationUnitField.getText().trim(),
                    localityField.getText().trim(),
                    stateField.getText().trim(),
                    countryField.getText().trim(),
                    Integer.parseInt(keySizeBox.getValue()),
                    Integer.parseInt(validityBox.getValue())
            );
        });

        dialog.showAndWait().ifPresent(request -> {
            boolean changedInMemory = false;
            try {
                document.addGeneratedAlias(request);
                changedInMemory = true;
                document.save();
                refreshAliases();
                selectAlias(request.alias());
                setStatus("已新增 alias: " + request.alias() + "。", false);
            } catch (Exception ex) {
                if (changedInMemory) {
                    recoverAfterMutatingFailure("新增失败", ex, "新增 alias 失败，已尝试重新加载磁盘中的原文件。");
                } else {
                    showError("新增失败", userFacingMessage(ex));
                    setStatus("新增 alias 失败。", true);
                }
            } finally {
                request.clearPasswords();
            }
        });
    }

    private void addField(GridPane grid, int row, String label, javafx.scene.Node input) {
        Label key = new Label(label);
        key.getStyleClass().add("form-key");
        grid.add(key, 0, row);
        grid.add(input, 1, row);
        GridPane.setHgrow(input, Priority.ALWAYS);
        if (input instanceof javafx.scene.control.Control control) {
            control.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private void deleteSelectedAlias() {
        AliasInfo selected = aliasTable.getSelectionModel().getSelectedItem();
        if (selected == null || document == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("删除 alias");
        confirm.setHeaderText("确认删除 " + selected.getAlias() + "？");
        confirm.setContentText("删除后会立即保存到当前 JKS 文件。");
        confirm.initOwner(stage);
        styleDialog(confirm.getDialogPane());
        Button okButton = (Button) confirm.getDialogPane().lookupButton(ButtonType.OK);
        okButton.getStyleClass().add("danger-button");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        boolean changedInMemory = false;
        try {
            document.deleteAlias(selected.getAlias());
            changedInMemory = true;
            document.save();
            failedPasswordsByAlias.remove(selected.getAlias());
            refreshAliases();
            setStatus("已删除 alias: " + selected.getAlias() + "。", false);
        } catch (Exception ex) {
            if (changedInMemory) {
                recoverAfterMutatingFailure("删除失败", ex, "删除 alias 失败，已尝试重新加载磁盘中的原文件。");
            } else {
                showError("删除失败", userFacingMessage(ex));
                setStatus("删除 alias 失败。", true);
            }
        }
    }

    private void verifySelectedAliasPassword() {
        AliasInfo selected = aliasTable.getSelectionModel().getSelectedItem();
        if (selected == null || document == null) {
            return;
        }
        String passwordText = aliasPasswordField.getText();
        char[] password = passwordText.toCharArray();
        try {
            boolean ok = document.verifyAliasPassword(selected.getAlias(), password);
            if (ok) {
                removeFailedPassword(selected.getAlias(), passwordText);
                setPasswordFeedback("密码正确，可以解锁 alias [" + selected.getAlias() + "]。", "success");
                setStatus("alias [" + selected.getAlias() + "] 密码正确。", false);
            } else {
                rememberFailedPassword(selected.getAlias(), passwordText);
                setPasswordFeedback("密码不正确，已加入当前 alias 的失败记录。", "error");
                setStatus("alias [" + selected.getAlias() + "] 密码不正确。", true);
            }
        } catch (Exception ex) {
            showError("验证失败", userFacingMessage(ex));
            setPasswordFeedback("验证失败：" + userFacingMessage(ex), "error");
            setStatus("验证失败。", true);
        } finally {
            Arrays.fill(password, '\0');
            aliasPasswordField.clear();
        }
    }

    private Optional<char[]> showPasswordDialog(String title, String header, String prompt) {
        Dialog<char[]> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        styleDialog(dialog.getDialogPane());
        ButtonType okType = new ButtonType("确定", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(prompt);
        passwordField.setMinWidth(320);
        VBox content = new VBox(10, new Label("密码"), createRevealablePasswordInput(passwordField));
        content.setPadding(new Insets(8, 0, 0, 0));
        dialog.getDialogPane().setContent(content);

        Button okButton = (Button) dialog.getDialogPane().lookupButton(okType);
        okButton.getStyleClass().add("primary-button");
        dialog.setResultConverter(button -> button == okType ? passwordField.getText().toCharArray() : null);
        return dialog.showAndWait();
    }

    private HBox createRevealablePasswordInput(PasswordField passwordField) {
        TextField visibleField = new TextField();
        visibleField.promptTextProperty().bind(passwordField.promptTextProperty());
        visibleField.textProperty().bindBidirectional(passwordField.textProperty());
        visibleField.setVisible(false);
        visibleField.setManaged(false);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        visibleField.setMaxWidth(Double.MAX_VALUE);

        StackPane fieldStack = new StackPane(passwordField, visibleField);
        fieldStack.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(fieldStack, Priority.ALWAYS);

        Button toggleButton = new Button("显示");
        toggleButton.getStyleClass().add("quiet-button");
        toggleButton.setTooltip(new Tooltip("显示或隐藏当前输入的密码"));
        toggleButton.setOnAction(event -> {
            boolean show = !visibleField.isVisible();
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
            visibleField.setVisible(show);
            visibleField.setManaged(show);
            toggleButton.setText(show ? "隐藏" : "显示");
            if (show) {
                visibleField.requestFocus();
                visibleField.positionCaret(visibleField.getText().length());
            } else {
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
            }
        });

        HBox row = new HBox(8, fieldStack, toggleButton);
        row.getStyleClass().add("password-row");
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private void refreshAliases() throws Exception {
        List<AliasInfo> fresh = document.listAliases();
        aliases.setAll(fresh);
        updateDocumentSummary();
        renderAliasDetails(aliasTable.getSelectionModel().getSelectedItem());
        updateActionState();
    }

    private void selectAlias(String alias) {
        for (AliasInfo info : aliases) {
            if (info.getAlias().equals(alias)) {
                aliasTable.getSelectionModel().select(info);
                aliasTable.scrollTo(info);
                break;
            }
        }
    }

    private void updateDocumentSummary() {
        boolean hasDocument = document != null;
        Path path = hasDocument ? document.path() : null;
        fileNameLabel.setText(hasDocument ? path.getFileName().toString() : "未打开");
        filePathLabel.setText(hasDocument ? path.toAbsolutePath().toString() : "打开 JKS 后会在这里显示文件路径");
        aliasCountLabel.setText(Integer.toString(aliases.size()));
        storeTypeLabel.setText(hasDocument ? "格式: " + document.storeType() : "格式: -");
        saveButton.setDisable(!hasDocument);
        addButton.setDisable(!hasDocument);
    }

    private void updateActionState() {
        boolean hasSelection = aliasTable.getSelectionModel().getSelectedItem() != null;
        deleteButton.setDisable(document == null || !hasSelection);
        verifyButton.setDisable(document == null || !hasSelection);
        aliasPasswordField.setDisable(document == null || !hasSelection);
        visibleAliasPasswordField.setDisable(document == null || !hasSelection);
        togglePasswordButton.setDisable(document == null || !hasSelection);
        refreshFailedPasswordList();
    }

    private void renderAliasDetails(AliasInfo info) {
        if (info == null) {
            detailAlias.setText("请选择 alias");
            detailType.setText("-");
            detailSubject.setText("-");
            detailIssuer.setText("-");
            detailValidity.setText("-");
            detailSerial.setText("-");
            detailAlgorithm.setText("-");
            setPasswordFeedback("等待选择 alias", "neutral");
            refreshFailedPasswordList();
            return;
        }
        detailAlias.setText(info.getAlias());
        detailType.setText(info.getKind());
        detailSubject.setText(info.getSubject());
        detailIssuer.setText(info.getIssuer());
        detailValidity.setText(info.getValidityText());
        detailSerial.setText(info.getSerialNumber());
        detailAlgorithm.setText(info.getSignatureAlgorithm() + " / " + info.getPublicKeyAlgorithm());
        setPasswordFeedback("等待输入 alias [" + info.getAlias() + "] 的密码", "neutral");
        refreshFailedPasswordList();
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message);
        statusLabel.pseudoClassStateChanged(AliasInfo.ERROR_PSEUDO_CLASS, error);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "未知错误" : message);
        alert.initOwner(stage);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void styleDialog(DialogPane dialogPane) {
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        dialogPane.getStyleClass().add("app-dialog");
    }

    private void clearCurrentDocument() {
        if (document != null) {
            document.clearPassword();
        }
        aliases.clear();
        failedPasswordsByAlias.clear();
        failedPasswordItems.clear();
        aliasPasswordField.clear();
        visibleAliasPasswordField.clear();
        showingAliasPassword = false;
        syncPasswordVisibility();
        document = null;
        updateDocumentSummary();
        renderAliasDetails(null);
        updateActionState();
    }

    private void toggleAliasPasswordVisibility() {
        showingAliasPassword = !showingAliasPassword;
        syncPasswordVisibility();
        if (showingAliasPassword) {
            visibleAliasPasswordField.requestFocus();
            visibleAliasPasswordField.positionCaret(visibleAliasPasswordField.getText().length());
        } else {
            aliasPasswordField.requestFocus();
            aliasPasswordField.positionCaret(aliasPasswordField.getText().length());
        }
    }

    private void syncPasswordVisibility() {
        aliasPasswordField.setVisible(!showingAliasPassword);
        aliasPasswordField.setManaged(!showingAliasPassword);
        visibleAliasPasswordField.setVisible(showingAliasPassword);
        visibleAliasPasswordField.setManaged(showingAliasPassword);
        if (togglePasswordButton != null) {
            togglePasswordButton.setText(showingAliasPassword ? "隐藏" : "显示");
        }
    }

    private void rememberFailedPassword(String alias, String passwordText) {
        if (passwordText == null || passwordText.isEmpty()) {
            return;
        }
        failedPasswordsByAlias
                .computeIfAbsent(alias, key -> new LinkedHashSet<>())
                .add(passwordText);
        refreshFailedPasswordList();
    }

    private void removeFailedPassword(String alias, String passwordText) {
        Set<String> failedPasswords = failedPasswordsByAlias.get(alias);
        if (failedPasswords == null) {
            return;
        }
        failedPasswords.remove(passwordText);
        if (failedPasswords.isEmpty()) {
            failedPasswordsByAlias.remove(alias);
        }
        refreshFailedPasswordList();
    }

    private void refreshFailedPasswordList() {
        AliasInfo selected = aliasTable == null ? null : aliasTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            failedPasswordItems.clear();
            return;
        }
        Set<String> failedPasswords = failedPasswordsByAlias.get(selected.getAlias());
        if (failedPasswords == null || failedPasswords.isEmpty()) {
            failedPasswordItems.clear();
            return;
        }
        failedPasswordItems.setAll(new ArrayList<>(failedPasswords));
    }

    private void setPasswordFeedback(String message, String state) {
        if (passwordFeedbackLabel == null) {
            return;
        }
        passwordFeedbackLabel.setText(message);
        passwordFeedbackLabel.getStyleClass().removeAll("feedback-success", "feedback-error", "feedback-neutral");
        passwordFeedbackLabel.getStyleClass().add("feedback-" + state);
    }

    private void recoverAfterMutatingFailure(String title, Exception ex, String statusMessage) {
        try {
            if (document != null) {
                document.reload();
                refreshAliases();
            }
        } catch (Exception reloadFailure) {
            ex.addSuppressed(reloadFailure);
        }
        showError(title, userFacingMessage(ex));
        setStatus(statusMessage, true);
    }

    private String userFacingMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        Throwable cause = ex.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            message += System.lineSeparator() + "原因: " + cause.getMessage();
        }
        Throwable[] suppressed = ex.getSuppressed();
        if (suppressed.length > 0) {
            message += System.lineSeparator() + "恢复提示: " + suppressed[0].getMessage();
        }
        return message;
    }
}
