package cn.silentcrane.jksviewer;

import cn.silentcrane.jksviewer.model.AliasInfo;
import cn.silentcrane.jksviewer.model.GeneratedAliasRequest;
import cn.silentcrane.jksviewer.service.CrashReporter;
import cn.silentcrane.jksviewer.service.KeystoreDocument;
import cn.silentcrane.jksviewer.service.backup.WebDavBackupRequest;
import cn.silentcrane.jksviewer.service.backup.WebDavBackupService;
import cn.silentcrane.jksviewer.service.update.ReleaseAsset;
import cn.silentcrane.jksviewer.service.update.ReleaseInfo;
import cn.silentcrane.jksviewer.service.update.UpdateCheckResult;
import cn.silentcrane.jksviewer.service.update.UpdateInstallAction;
import cn.silentcrane.jksviewer.service.update.UpdateService;
import java.awt.Desktop;
import java.io.File;
import java.net.URI;
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
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
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
    private static final String APP_ICON_RESOURCE = "/icons/app.png";

    private final ObservableList<AliasInfo> aliases = FXCollections.observableArrayList();
    private final ObservableList<String> failedPasswordItems = FXCollections.observableArrayList();
    private final Map<String, LinkedHashSet<String>> failedPasswordsByAlias = new HashMap<>();
    private final AppMetadata appMetadata = AppMetadata.load();
    private final UpdateService updateService = new UpdateService(appMetadata);
    private final WebDavBackupService backupService = new WebDavBackupService();

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
    private Button discardButton;
    private Button backupButton;
    private Button verifyButton;
    private Label passwordFeedbackLabel;
    private ListView<String> failedPasswordList;
    private StackPane dropOverlay;
    private boolean showingAliasPassword;
    private boolean hasUnsavedChanges;
    private boolean newDocumentPendingSave;

    public static void main(String[] args) {
        CrashReporter.install(JksViewerApp.class, AppMetadata.load());
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        CrashReporter.attachCurrentThread();
        this.stage = primaryStage;
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(createHeader());
        root.setLeft(createSidebar());
        root.setCenter(createAliasTable());
        root.setRight(createInspector());
        root.setBottom(createStatusBar());

        StackPane shell = new StackPane(root, createDropOverlay());
        shell.getStyleClass().add("app-shell");
        configureFileDrop(shell);

        Scene scene = new Scene(shell, 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        primaryStage.setTitle("JKS Viewer");
        primaryStage.getIcons().add(new Image(getClass().getResource(APP_ICON_RESOURCE).toExternalForm()));
        primaryStage.setMinWidth(1020);
        primaryStage.setMinHeight(660);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            if (!confirmDiscardUnsavedChanges("关闭程序")) {
                event.consume();
            }
        });
        primaryStage.show();
        updateDocumentSummary();
        setStatus("请选择或拖入一个 Android JKS 文件开始。", false);
    }

    @Override
    public void stop() {
        if (document != null) {
            document.clearPassword();
        }
    }

    private StackPane createDropOverlay() {
        Label title = new Label("松开打开密钥库文件");
        title.getStyleClass().add("drop-overlay-title");
        Label subtitle = new Label("将使用该文件的库密码继续打开");
        subtitle.getStyleClass().add("drop-overlay-subtitle");

        VBox card = new VBox(6, title, subtitle);
        card.getStyleClass().add("drop-overlay-card");
        card.setAlignment(Pos.CENTER);

        dropOverlay = new StackPane(card);
        dropOverlay.getStyleClass().add("drop-overlay");
        dropOverlay.setVisible(false);
        dropOverlay.setMouseTransparent(true);
        return dropOverlay;
    }

    private void configureFileDrop(StackPane dropTarget) {
        dropTarget.setOnDragOver(event -> {
            if (isSupportedFileDrag(event.getDragboard())) {
                event.acceptTransferModes(TransferMode.COPY);
                setDropOverlayVisible(true);
            }
            event.consume();
        });

        dropTarget.setOnDragEntered(event -> {
            if (isSupportedFileDrag(event.getDragboard())) {
                setDropOverlayVisible(true);
            }
            event.consume();
        });

        dropTarget.setOnDragExited(event -> {
            if (!dropTarget.getBoundsInLocal().contains(dropTarget.sceneToLocal(event.getSceneX(), event.getSceneY()))) {
                setDropOverlayVisible(false);
            }
            event.consume();
        });

        dropTarget.setOnDragDropped(event -> {
            File file = firstDraggedFile(event.getDragboard());
            setDropOverlayVisible(false);
            if (file == null) {
                event.setDropCompleted(false);
            } else {
                openKeystoreFile(file);
                event.setDropCompleted(true);
            }
            event.consume();
        });
    }

    private boolean isSupportedFileDrag(Dragboard dragboard) {
        return firstDraggedFile(dragboard) != null;
    }

    private File firstDraggedFile(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasFiles()) {
            return null;
        }
        return dragboard.getFiles().stream()
                .filter(File::isFile)
                .findFirst()
                .orElse(null);
    }

    private void setDropOverlayVisible(boolean visible) {
        if (dropOverlay == null || dropOverlay.isVisible() == visible) {
            return;
        }
        dropOverlay.setVisible(visible);
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

        discardButton = new Button("放弃修改");
        discardButton.getStyleClass().addAll("danger-button", "toolbar-button");
        discardButton.setTooltip(new Tooltip("放弃尚未保存的新增或删除"));
        discardButton.setDisable(true);
        discardButton.setOnAction(event -> discardUnsavedChanges());

        backupButton = new Button("WebDAV 备份");
        backupButton.getStyleClass().addAll("quiet-button", "toolbar-button");
        backupButton.setTooltip(new Tooltip("备份指定的密钥文件到 WebDAV"));
        backupButton.setOnAction(event -> showWebDavBackupDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(14, copy, spacer, openButton, newButton, backupButton, discardButton, saveButton, createAboutMenu());
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private MenuButton createAboutMenu() {
        MenuItem aboutItem = new MenuItem("关于 " + appMetadata.name());
        aboutItem.setOnAction(event -> showAboutDialog(false));

        MenuItem checkUpdateItem = new MenuItem("检查更新");
        checkUpdateItem.setOnAction(event -> showAboutDialog(true));

        MenuItem developerHomeItem = new MenuItem("开发者主页");
        developerHomeItem.setOnAction(event -> openExternalUri(appMetadata.developerHomepageUrl()));

        MenuItem repositoryItem = new MenuItem("GitHub 仓库");
        repositoryItem.setOnAction(event -> openExternalUri(appMetadata.repositoryUrl()));

        MenuItem licenseItem = new MenuItem("许可证");
        licenseItem.setOnAction(event -> showLicenseDialog());

        MenuButton aboutMenu = new MenuButton("关于");
        aboutMenu.getStyleClass().addAll("quiet-button", "toolbar-button");
        aboutMenu.setTooltip(new Tooltip("查看应用信息、开发者主页、许可证和更新"));
        aboutMenu.getItems().addAll(
                aboutItem,
                checkUpdateItem,
                new SeparatorMenuItem(),
                developerHomeItem,
                repositoryItem,
                licenseItem
        );
        return aboutMenu;
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

        openKeystoreFile(file);
    }

    private void openKeystoreFile(File file) {
        Optional<char[]> password = showPasswordDialog("输入库密码", "请输入 JKS 文件的库密码。", "库密码");
        password.ifPresent(chars -> {
            KeystoreDocument loaded = null;
            try {
                loaded = KeystoreDocument.load(file.toPath(), chars);
                if (!confirmDiscardUnsavedChanges("打开其他文件")) {
                    return;
                }
                clearCurrentDocument();
                document = loaded;
                loaded = null;
                hasUnsavedChanges = false;
                newDocumentPendingSave = false;
                refreshAliases();
                setStatus("已打开 " + file.getName() + "，格式 " + document.storeType() + "。", false);
            } catch (Exception ex) {
                showError("打开失败", userFacingMessage(ex));
                setStatus("打开失败，请确认库密码和文件格式。", true);
            } finally {
                if (loaded != null) {
                    loaded.clearPassword();
                }
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
            KeystoreDocument created = null;
            try {
                if (chars.length < 6) {
                    throw new IllegalArgumentException("库密码至少需要 6 位。");
                }
                created = KeystoreDocument.create(file.toPath(), chars);
                if (!confirmDiscardUnsavedChanges("新建文件")) {
                    return;
                }
                clearCurrentDocument();
                document = created;
                created = null;
                hasUnsavedChanges = true;
                newDocumentPendingSave = true;
                refreshAliases();
                setStatus("已预存新的 JKS 文件，点击“保存文件”后写入磁盘。", false);
            } catch (Exception ex) {
                showError("创建失败", userFacingMessage(ex));
                setStatus("创建失败。", true);
            } finally {
                if (created != null) {
                    created.clearPassword();
                }
                Arrays.fill(chars, '\0');
            }
        });
    }

    private void saveKeystore() {
        if (document == null) {
            return;
        }
        if (!hasUnsavedChanges) {
            setStatus("当前没有需要保存的修改。", false);
            return;
        }
        try {
            document.save();
            hasUnsavedChanges = false;
            newDocumentPendingSave = false;
            updateDocumentSummary();
            setStatus("已保存到 " + document.path().getFileName() + "。", false);
        } catch (Exception ex) {
            showError("保存失败", userFacingMessage(ex));
            setStatus("保存失败。", true);
        }
    }

    private void discardUnsavedChanges() {
        if (document == null || !hasUnsavedChanges) {
            return;
        }
        if (!confirmDiscardUnsavedChanges("放弃修改")) {
            return;
        }
        try {
            if (newDocumentPendingSave) {
                clearCurrentDocument();
                setStatus("已放弃新建的 JKS 文件。", false);
                return;
            }
            document.reload();
            hasUnsavedChanges = false;
            clearAliasInteractionState();
            refreshAliases();
            setStatus("已放弃未保存修改，并从磁盘重新加载。", false);
        } catch (Exception ex) {
            showError("放弃修改失败", userFacingMessage(ex));
            setStatus("放弃修改失败。", true);
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
        TextField commonNameField = new TextField();
        commonNameField.setPromptText("例如 yin2hao");
        TextField organizationField = new TextField();
        TextField organizationUnitField = new TextField();
        TextField localityField = new TextField();
        TextField stateField = new TextField();
        TextField countryField = new TextField();
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
        addField(form, 2, "姓名 CN", commonNameField);
        addField(form, 3, "部门 OU", organizationUnitField);
        addField(form, 4, "组织 O", organizationField);
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
            try {
                document.addGeneratedAlias(request);
                hasUnsavedChanges = true;
                refreshAliases();
                selectAlias(request.alias());
                setStatus("已预存新增 alias: " + request.alias() + "，点击“保存文件”后写入磁盘。", false);
            } catch (Exception ex) {
                showError("新增失败", userFacingMessage(ex));
                setStatus("新增 alias 失败。", true);
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
        confirm.setContentText("删除会先保存在当前编辑内容中，点击“保存文件”后才会写入磁盘。");
        confirm.initOwner(stage);
        styleDialog(confirm.getDialogPane());
        Button okButton = (Button) confirm.getDialogPane().lookupButton(ButtonType.OK);
        okButton.getStyleClass().add("danger-button");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }
        try {
            document.deleteAlias(selected.getAlias());
            hasUnsavedChanges = true;
            failedPasswordsByAlias.remove(selected.getAlias());
            refreshAliases();
            setStatus("已预存删除 alias: " + selected.getAlias() + "，点击“保存文件”后写入磁盘。", false);
        } catch (Exception ex) {
            showError("删除失败", userFacingMessage(ex));
            setStatus("删除 alias 失败。", true);
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

    private void showWebDavBackupDialog() {
        Dialog<WebDavBackupRequest> dialog = new Dialog<>();
        dialog.setTitle("WebDAV 备份");
        dialog.setHeaderText("备份密钥文件到 WebDAV");
        styleDialog(dialog.getDialogPane());
        dialog.getDialogPane().setMinWidth(640);
        dialog.getDialogPane().setPrefWidth(680);

        ButtonType backupType = new ButtonType("开始备份", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(backupType, ButtonType.CANCEL);

        TextField sourceField = new TextField(defaultBackupSourcePath());
        sourceField.setPromptText("选择 .jks / .keystore / .p12 文件");
        Button chooseButton = new Button("选择");
        chooseButton.getStyleClass().add("quiet-button");
        chooseButton.setOnAction(event -> chooseBackupSourceFile(sourceField));
        HBox sourceRow = new HBox(8, sourceField, chooseButton);
        sourceRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(sourceField, Priority.ALWAYS);

        TextField urlField = new TextField();
        urlField.setPromptText("https://example.com/dav/");
        TextField directoryField = new TextField("jks-backup");
        directoryField.setPromptText("远程目录");
        TextField usernameField = new TextField();
        usernameField.setPromptText("用户名");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("密码或应用专用密码");

        GridPane form = new GridPane();
        form.getStyleClass().add("alias-form");
        form.setMinWidth(584);
        form.setPrefWidth(584);
        form.setHgap(12);
        form.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints(96);
        ColumnConstraints inputColumn = new ColumnConstraints(476);
        inputColumn.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labelColumn, inputColumn);
        addField(form, 0, "密钥文件", sourceRow);
        addField(form, 1, "WebDAV 地址", urlField);
        addField(form, 2, "远程目录", directoryField);
        addField(form, 3, "用户名", usernameField);
        addField(form, 4, "密码", createRevealablePasswordInput(passwordField));

        Label hint = new Label(hasUnsavedChanges
                ? "当前文件有未保存修改，备份会上传磁盘上已保存的文件内容。"
                : "远程目录不存在时会自动创建。");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);

        VBox content = new VBox(12, form, hint);
        content.setPadding(new Insets(4, 0, 0, 0));
        dialog.getDialogPane().setContent(content);

        Button backupButtonNode = (Button) dialog.getDialogPane().lookupButton(backupType);
        backupButtonNode.getStyleClass().add("primary-button");
        backupButtonNode.disableProperty().bind(Bindings.createBooleanBinding(
                () -> sourceField.getText().isBlank() || urlField.getText().isBlank(),
                sourceField.textProperty(),
                urlField.textProperty()
        ));
        backupButtonNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                Path.of(sourceField.getText().trim());
                URI.create(urlField.getText().trim());
            } catch (IllegalArgumentException ex) {
                showError("备份参数错误", userFacingMessage(ex));
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != backupType) {
                return null;
            }
            return new WebDavBackupRequest(
                    Path.of(sourceField.getText().trim()),
                    URI.create(urlField.getText().trim()),
                    directoryField.getText().trim(),
                    usernameField.getText().trim(),
                    passwordField.getText().toCharArray()
            );
        });

        dialog.showAndWait().ifPresent(this::startWebDavBackup);
    }

    private String defaultBackupSourcePath() {
        if (document == null || newDocumentPendingSave) {
            return "";
        }
        return document.path().toAbsolutePath().toString();
    }

    private void chooseBackupSourceFile(TextField sourceField) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要备份的密钥文件");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Android / Java KeyStore", "*.jks", "*.keystore", "*.p12", "*.pfx", "*.bks", "*.bcfks"),
                new FileChooser.ExtensionFilter("所有文件", "*.*")
        );
        if (!sourceField.getText().isBlank()) {
            File current = new File(sourceField.getText().trim());
            File parent = current.isDirectory() ? current : current.getParentFile();
            if (parent != null && parent.isDirectory()) {
                chooser.setInitialDirectory(parent);
            }
            if (current.isFile()) {
                chooser.setInitialFileName(current.getName());
            }
        }
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            sourceField.setText(file.toPath().toAbsolutePath().toString());
        }
    }

    private void startWebDavBackup(WebDavBackupRequest request) {
        setStatus("正在备份 " + request.sourceFile().getFileName() + " 到 WebDAV。", false);
        backupButton.setDisable(true);

        Task<URI> task = new Task<>() {
            @Override
            protected URI call() throws Exception {
                return backupService.backup(request);
            }
        };
        task.setOnSucceeded(event -> {
            request.clearPassword();
            backupButton.setDisable(false);
            URI targetUri = task.getValue();
            setStatus("WebDAV 备份完成: " + targetUri, false);
            showInfo("备份完成", "已备份到:" + System.lineSeparator() + targetUri);
        });
        task.setOnFailed(event -> {
            request.clearPassword();
            backupButton.setDisable(false);
            showError("备份失败", userFacingMessage(task.getException()));
            setStatus("WebDAV 备份失败。", true);
        });
        startDaemonTask(task, "jksviewer-webdav-backup");
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
        fileNameLabel.setText(hasDocument ? displayFileName(path) : "未打开");
        filePathLabel.setText(hasDocument ? displayFilePath(path) : "打开 JKS 后会在这里显示文件路径");
        aliasCountLabel.setText(Integer.toString(aliases.size()));
        storeTypeLabel.setText(hasDocument ? displayStoreType() : "格式: -");
        saveButton.setDisable(!hasDocument || !hasUnsavedChanges);
        saveButton.setText(hasUnsavedChanges ? "保存文件 *" : "保存文件");
        discardButton.setDisable(!hasDocument || !hasUnsavedChanges);
        addButton.setDisable(!hasDocument);
    }

    private String displayFileName(Path path) {
        String name = path.getFileName().toString();
        return hasUnsavedChanges ? name + "（未保存）" : name;
    }

    private String displayFilePath(Path path) {
        String absolutePath = path.toAbsolutePath().toString();
        if (newDocumentPendingSave) {
            return "尚未写入磁盘，保存后写入: " + absolutePath;
        }
        return absolutePath;
    }

    private String displayStoreType() {
        String text = "格式: " + document.storeType();
        return hasUnsavedChanges ? text + " · 未保存修改" : text;
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

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "" : message);
        alert.initOwner(stage);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private void showAboutDialog(boolean checkImmediately) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("关于 " + appMetadata.name());
        dialog.setHeaderText(appMetadata.name());
        styleDialog(dialog.getDialogPane());
        dialog.getDialogPane().setMinWidth(520);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));

        Label title = new Label(appMetadata.name());
        title.getStyleClass().add("about-title");
        Label subtitle = new Label("Android 签名库可视化管理");
        subtitle.getStyleClass().add("muted-label");

        GridPane info = new GridPane();
        info.getStyleClass().add("detail-grid");
        info.setHgap(12);
        info.setVgap(10);
        info.getColumnConstraints().addAll(new ColumnConstraints(88), new ColumnConstraints(360));
        addAboutRow(info, 0, "当前版本", appMetadata.version());
        addAboutRow(info, 1, "开发者", appMetadata.vendor());
        addAboutRow(info, 2, "许可证", displayLicense());
        addAboutRow(info, 3, "运行模式", appMetadata.isPortableRuntime() ? "Portable，可自动覆盖更新" : "安装版，下载后启动安装包");

        Button developerButton = new Button("开发者主页");
        developerButton.getStyleClass().add("quiet-button");
        developerButton.setOnAction(event -> openExternalUri(appMetadata.developerHomepageUrl()));

        Button repositoryButton = new Button("GitHub 仓库");
        repositoryButton.getStyleClass().add("quiet-button");
        repositoryButton.setOnAction(event -> openExternalUri(appMetadata.repositoryUrl()));

        Button checkButton = new Button("检查更新");
        checkButton.getStyleClass().add("primary-button");

        Button installButton = new Button(appMetadata.isPortableRuntime() ? "下载并自动覆盖" : "下载安装包");
        installButton.getStyleClass().add("quiet-button");
        installButton.setDisable(true);

        Label updateStatus = new Label("点击“检查更新”从 GitHub Release 获取最新版本。");
        updateStatus.getStyleClass().add("update-status");
        updateStatus.setWrapText(true);

        checkButton.setOnAction(event -> checkForUpdates(updateStatus, checkButton, installButton));

        HBox linkActions = new HBox(8, developerButton, repositoryButton);
        linkActions.getStyleClass().add("about-actions");
        HBox updateActions = new HBox(8, checkButton, installButton);
        updateActions.getStyleClass().add("about-actions");

        VBox content = new VBox(14, title, subtitle, info, linkActions, new Separator(), updateStatus, updateActions);
        content.getStyleClass().add("about-content");
        dialog.getDialogPane().setContent(content);
        dialog.setOnShown(event -> {
            if (checkImmediately) {
                checkForUpdates(updateStatus, checkButton, installButton);
            }
        });
        dialog.showAndWait();
    }

    private void addAboutRow(GridPane grid, int row, String key, String value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("detail-key");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("detail-value");
        valueLabel.setWrapText(true);
        grid.add(keyLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private void showLicenseDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("许可证");
        alert.setHeaderText("许可证信息");
        alert.setContentText("许可证: " + displayLicense() + System.lineSeparator()
                + "最新授权条款请以 GitHub 仓库中的许可证文件为准。");
        alert.initOwner(stage);
        styleDialog(alert.getDialogPane());
        alert.showAndWait();
    }

    private String displayLicense() {
        return "Unspecified".equalsIgnoreCase(appMetadata.license())
                ? "未声明"
                : appMetadata.license();
    }

    private void checkForUpdates(Label updateStatus, Button checkButton, Button installButton) {
        checkButton.setDisable(true);
        installButton.setDisable(true);
        updateStatus.setText("正在连接 GitHub Release...");
        setStatus("正在检查更新。", false);

        Task<UpdateCheckResult> task = new Task<>() {
            @Override
            protected UpdateCheckResult call() throws Exception {
                return updateService.checkLatest();
            }
        };
        task.setOnSucceeded(event -> renderUpdateResult(task.getValue(), updateStatus, checkButton, installButton));
        task.setOnFailed(event -> {
            checkButton.setDisable(false);
            installButton.setDisable(true);
            String message = userFacingMessage(task.getException());
            updateStatus.setText("检查更新失败: " + message);
            setStatus("检查更新失败。", true);
        });
        startDaemonTask(task, "jksviewer-update-check");
    }

    private void renderUpdateResult(
            UpdateCheckResult result,
            Label updateStatus,
            Button checkButton,
            Button installButton
    ) {
        checkButton.setDisable(false);
        ReleaseInfo release = result.latestRelease();
        if (!result.updateAvailable()) {
            installButton.setDisable(true);
            updateStatus.setText("已是最新版本: " + result.currentVersion() + "。");
            setStatus("当前已是最新版本。", false);
            return;
        }

        Optional<ReleaseAsset> asset = updateService.preferredAsset(release);
        String releaseText = "发现新版本 " + release.version() + "（当前 " + result.currentVersion() + "）。";
        if (asset.isEmpty()) {
            installButton.setText("打开 Release");
            installButton.setDisable(false);
            installButton.setOnAction(event -> openExternalUri(release.htmlUri().toString()));
            updateStatus.setText(releaseText + " 未找到可自动处理的 Windows 发布包，可打开 Release 页面手动下载。");
            setStatus("发现新版本 " + release.version() + "。", false);
            return;
        }

        ReleaseAsset updateAsset = asset.get();
        installButton.setText(appMetadata.isPortableRuntime() ? "下载并自动覆盖" : "下载安装包");
        installButton.setDisable(false);
        installButton.setOnAction(event -> confirmAndInstallUpdate(release, updateAsset, updateStatus, installButton));
        updateStatus.setText(releaseText + " 可用发布包: " + updateAsset.name() + "。");
        setStatus("发现新版本 " + release.version() + "。", false);
    }

    private void confirmAndInstallUpdate(
            ReleaseInfo release,
            ReleaseAsset asset,
            Label updateStatus,
            Button installButton
    ) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("更新 " + appMetadata.name());
        confirm.setHeaderText(appMetadata.isPortableRuntime() ? "下载并自动覆盖旧版本？" : "下载并启动安装包？");
        confirm.setContentText(appMetadata.isPortableRuntime()
                ? "将下载 " + release.version() + " 的 portable 包。下载完成后程序会退出，等待当前进程结束后覆盖旧目录并自动重启。"
                : "将下载 " + release.version() + " 的安装包并启动安装程序，请按安装器提示完成覆盖安装。");
        confirm.initOwner(stage);
        styleDialog(confirm.getDialogPane());

        ButtonType updateType = new ButtonType("开始更新", ButtonBar.ButtonData.OK_DONE);
        confirm.getButtonTypes().setAll(updateType, ButtonType.CANCEL);
        Button updateButton = (Button) confirm.getDialogPane().lookupButton(updateType);
        updateButton.getStyleClass().add("primary-button");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != updateType) {
            return;
        }
        downloadAndInstallUpdate(asset, updateStatus, installButton);
    }

    private void downloadAndInstallUpdate(ReleaseAsset asset, Label updateStatus, Button installButton) {
        installButton.setDisable(true);
        updateStatus.setText("正在下载更新包: " + asset.name() + "...");
        setStatus("正在下载更新包。", false);

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                return updateService.downloadAsset(asset);
            }
        };
        task.setOnSucceeded(event -> {
            try {
                UpdateInstallAction action = updateService.installDownloadedAsset(asset, task.getValue());
                if (action == UpdateInstallAction.PORTABLE_UPDATE_STARTED) {
                    updateStatus.setText("更新脚本已启动，程序即将关闭并自动覆盖旧版本。");
                    setStatus("更新脚本已启动，正在退出程序。", false);
                    Platform.exit();
                } else if (action == UpdateInstallAction.INSTALLER_STARTED) {
                    updateStatus.setText("安装程序已启动，请按提示完成覆盖安装。");
                    setStatus("安装程序已启动。", false);
                } else {
                    updateStatus.setText("更新包已下载并打开，请手动完成更新。");
                    setStatus("更新包已打开。", false);
                }
            } catch (Exception ex) {
                installButton.setDisable(false);
                updateStatus.setText("启动更新失败: " + userFacingMessage(ex));
                setStatus("启动更新失败。", true);
            }
        });
        task.setOnFailed(event -> {
            installButton.setDisable(false);
            updateStatus.setText("下载更新失败: " + userFacingMessage(task.getException()));
            setStatus("下载更新失败。", true);
        });
        startDaemonTask(task, "jksviewer-update-download");
    }

    private void openExternalUri(String uri) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("当前系统不支持打开浏览器。");
            }
            Desktop.getDesktop().browse(URI.create(uri));
        } catch (Exception ex) {
            showError("打开链接失败", userFacingMessage(ex));
        }
    }

    private void startDaemonTask(Task<?> task, String threadName) {
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        CrashReporter.attach(thread);
        thread.start();
    }

    private void styleDialog(DialogPane dialogPane) {
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        dialogPane.getStyleClass().add("app-dialog");
    }

    private boolean confirmDiscardUnsavedChanges(String action) {
        if (!hasUnsavedChanges) {
            return true;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(action);
        confirm.setHeaderText("有未保存的修改");
        confirm.setContentText("当前新增或删除的 alias 只预存在内存中。继续" + action + "会放弃这些未保存修改。");
        confirm.initOwner(stage);
        styleDialog(confirm.getDialogPane());

        ButtonType discardType = new ButtonType("放弃修改", ButtonBar.ButtonData.OK_DONE);
        confirm.getButtonTypes().setAll(discardType, ButtonType.CANCEL);
        Button discardButtonNode = (Button) confirm.getDialogPane().lookupButton(discardType);
        discardButtonNode.getStyleClass().add("danger-button");

        Optional<ButtonType> result = confirm.showAndWait();
        return result.isPresent() && result.get() == discardType;
    }

    private void clearCurrentDocument() {
        if (document != null) {
            document.clearPassword();
        }
        aliases.clear();
        clearAliasInteractionState();
        document = null;
        hasUnsavedChanges = false;
        newDocumentPendingSave = false;
        updateDocumentSummary();
        renderAliasDetails(null);
        updateActionState();
    }

    private void clearAliasInteractionState() {
        failedPasswordsByAlias.clear();
        failedPasswordItems.clear();
        aliasTable.getSelectionModel().clearSelection();
        aliasPasswordField.clear();
        visibleAliasPasswordField.clear();
        showingAliasPassword = false;
        syncPasswordVisibility();
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

    private String userFacingMessage(Throwable ex) {
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
