package application;

import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;

import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

// JavaFXを用いたMarkdownエディタのUI構築・描画更新・ダイアログ表示を担当するビュークラス。

/**
 * Markdownエディタの画面レイアウト、UIコンポーネント操作、およびダイアログ表示を担当するビュークラス。
 * <p>
 * メニューバー、分割ペイン（{@link TextArea} と {@link WebView}）、ファイル選択ダイアログ、
 * 確認／エラーダイアログを構築・管理し、イベント処理や業務状態はコントローラへ委譲する。
 * </p>
 */
public class EditorView {

    /**
     * 未保存変更の確認ダイアログにおけるユーザーの選択結果を表す列挙型。
     */
    public enum SaveConfirmationChoice {
        /** 変更を保存して続行する */
        SAVE,
        /** 変更を破棄して続行する */
        DISCARD,
        /** 操作をキャンセルして現在の状態を維持する */
        CANCEL
    }

    private static final String WINDOW_TITLE_SUFFIX = " - Markdownエディタ";
    private static final double INITIAL_WIDTH = 800;
    private static final double INITIAL_HEIGHT = 600;
    private static final double INITIAL_DIVIDER_POSITION = 0.3;

    private final Stage primaryStage;
    private final BorderPane root;
    private final FileChooser fileChooser = new FileChooser();

    private MenuItem newItem;
    private MenuItem openItem;
    private MenuItem saveItem;
    private MenuItem saveAsItem;
    private MenuItem exitItem;
    private RadioMenuItem noWrapItem;
    private RadioMenuItem wrapItem;
    private CheckMenuItem overwriteItem;

    private TextArea textArea;
    private WebView webView;

    /**
     * 指定されたステージおよび初期設定値を用いてビューとUIコンポーネントを構築する。
     *
     * @param primaryStage         アプリケーションのプライマリステージ
     * @param initialWrapText      テキスト折り返しの初期状態
     * @param initialOverwriteMode 上書きモードの初期状態
     */
    public EditorView(Stage primaryStage, boolean initialWrapText, boolean initialOverwriteMode) {
        // タイトル更新、ダイアログのオーナー指定、終了イベント発行のためStage参照を保持する
        this.primaryStage = primaryStage;
        this.root = new BorderPane();

        root.setTop(createMenuBar(initialWrapText, initialOverwriteMode));
        root.setCenter(createEditorSplitPane(initialWrapText));

        configureFileChooser();
    }

    /**
     * アプリケーション上部のメニューバーを構築する。
     *
     * @param initialWrapText      テキスト折り返しの初期状態
     * @param initialOverwriteMode 上書きモードの初期状態
     * @return 「ファイル」「設定」の順に配置された {@link MenuBar}
     */
    private MenuBar createMenuBar(boolean initialWrapText, boolean initialOverwriteMode) {
        MenuBar menuBar = new MenuBar();
        // デスクトップアプリの標準的な並び順に合わせて「ファイル」を左端、「設定」をその右に配置する
        menuBar.getMenus().addAll(
            createFileMenu(),
            createSettingsMenu(initialWrapText, initialOverwriteMode)
        );
        return menuBar;
    }

    /**
     * ファイル操作（新規作成・開く・保存・終了）を提供する「ファイル」メニューを構築する。
     *
     * @return 構築された「ファイル」{@link Menu}
     */
    private Menu createFileMenu() {
        Menu fileMenu = new Menu("ファイル");

        newItem = new MenuItem("新規作成");
        openItem = new MenuItem("開く...");
        saveItem = new MenuItem("上書き保存");
        saveAsItem = new MenuItem("名前を付けて保存...");
        exitItem = new MenuItem("終了");

        fileMenu.getItems().addAll(
            newItem,
            openItem,
            new SeparatorMenuItem(),
            saveItem,
            saveAsItem,
            new SeparatorMenuItem(),
            exitItem
        );
        return fileMenu;
    }

    /**
     * エディタの動作設定（テキスト折り返し・上書きモード）を提供する「設定」メニューを構築する。
     *
     * @param initialWrapText      テキスト折り返しの初期状態
     * @param initialOverwriteMode 上書きモードの初期状態
     * @return 構築された「設定」{@link Menu}
     */
    private Menu createSettingsMenu(boolean initialWrapText, boolean initialOverwriteMode) {
        Menu settingsMenu = new Menu("設定");

        ToggleGroup wrapToggleGroup = new ToggleGroup();

        noWrapItem = new RadioMenuItem("折り返さない");
        noWrapItem.setToggleGroup(wrapToggleGroup);

        wrapItem = new RadioMenuItem("右端で折り返す");
        wrapItem.setToggleGroup(wrapToggleGroup);
        // 初期状態とメニュー表示の整合を保つためコンストラクタ経由の初期フラグで選択状態を決める
        wrapItem.setSelected(initialWrapText);
        noWrapItem.setSelected(!initialWrapText);

        overwriteItem = new CheckMenuItem("挿入/上書き切り替え");
        overwriteItem.setSelected(initialOverwriteMode);

        settingsMenu.getItems().addAll(
            noWrapItem,
            wrapItem,
            new SeparatorMenuItem(),
            overwriteItem
        );
        return settingsMenu;
    }

    /**
     * Markdown入力用の {@link TextArea} とプレビュー用の {@link WebView} を並べた {@link SplitPane} を構築する。
     *
     * @param initialWrapText テキスト折り返しの初期状態
     * @return エディタとプレビューを含む {@link SplitPane}
     */
    private SplitPane createEditorSplitPane(boolean initialWrapText) {
        textArea = new TextArea();
        webView = new WebView();

        textArea.setPromptText("ここにMarkdownを入力...");
        textArea.setStyle("-fx-font-family: 'Yu Gothic UI'; -fx-font-size: 15px;");
        textArea.setWrapText(initialWrapText);

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(textArea, webView);
        splitPane.setDividerPositions(INITIAL_DIVIDER_POSITION);
        return splitPane;
    }

    /**
     * ファイルを開く／保存する際に使用する {@link FileChooser} のタイトルと拡張子フィルタを設定する。
     */
    private void configureFileChooser() {
        fileChooser.setTitle("Markdownファイルを選択");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Markdown ファイル", "*.md", "*.MD", "*.markdown"),
            new FileChooser.ExtensionFilter("すべてのファイル", "*.*")
        );
    }

    /**
     * 構築済みルートレイアウトを保持する {@link Scene} を生成して返す。
     *
     * @return 初期サイズ設定済みの {@link Scene}
     */
    public Scene createScene() {
        return new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
    }

    /**
     * 「新規作成」メニュー選択時のハンドラを設定する。
     *
     * @param handler 実行するコールバック
     */
    public void setOnFileNew(Runnable handler) {
        newItem.setOnAction(e -> handler.run());
    }

    /**
     * 「開く...」メニュー選択時のハンドラを設定する。
     *
     * @param handler 実行するコールバック
     */
    public void setOnFileOpen(Runnable handler) {
        openItem.setOnAction(e -> handler.run());
    }

    /**
     * 「上書き保存」メニュー選択時のハンドラを設定する。
     *
     * @param handler 実行するコールバック
     */
    public void setOnFileSave(Runnable handler) {
        saveItem.setOnAction(e -> handler.run());
    }

    /**
     * 「名前を付けて保存...」メニュー選択時のハンドラを設定する。
     *
     * @param handler 実行するコールバック
     */
    public void setOnFileSaveAs(Runnable handler) {
        saveAsItem.setOnAction(e -> handler.run());
    }

    /**
     * 「終了」メニュー選択時のハンドラを設定する。
     *
     * @param handler 実行するコールバック
     */
    public void setOnFileExit(Runnable handler) {
        exitItem.setOnAction(e -> handler.run());
    }

    /**
     * テキスト折り返し設定変更時のハンドラを設定する。
     *
     * @param handler 折り返し有効フラグを受け取るコールバック
     */
    public void setOnWrapTextChanged(Consumer<Boolean> handler) {
        noWrapItem.setOnAction(e -> handler.accept(false));
        wrapItem.setOnAction(e -> handler.accept(true));
    }

    /**
     * 上書きモード切り替え時のハンドラを設定する。
     *
     * @param handler 上書きモード有効フラグを受け取るコールバック
     */
    public void setOnOverwriteModeChanged(Consumer<Boolean> handler) {
        overwriteItem.setOnAction(e -> handler.accept(overwriteItem.isSelected()));
    }

    /**
     * エディタテキストの内容変更を監視するリスナーを追加する。
     *
     * @param listener 変更時に呼び出されるコールバック
     */
    public void addTextChangeListener(Runnable listener) {
        textArea.textProperty().addListener((observable, oldValue, newValue) -> listener.run());
    }

    /**
     * エディタへのキータイプイベントをキャプチャフェーズで処理するフィルタを追加する。
     *
     * @param filter 登録するキーイベントフィルタ
     */
    public void addKeyTypedFilter(EventHandler<KeyEvent> filter) {
        // setOnKeyTypedはTextAreaSkinの標準入力ハンドラより後に呼ばれるため、先に捕捉してconsume()できるようEventFilterで登録する
        textArea.addEventFilter(KeyEvent.KEY_TYPED, filter);
    }

    /**
     * ウィンドウの閉じる要求イベントハンドラを設定する。
     *
     * @param handler ウィンドウクローズイベントハンドラ
     */
    public void setOnWindowCloseRequest(EventHandler<WindowEvent> handler) {
        primaryStage.setOnCloseRequest(handler);
    }

    /**
     * 入力用の {@link TextArea} を返す。
     * <p>
     * 上書きモード時のキャレット位置・選択範囲の取得と部分置換に利用する。
     * </p>
     *
     * @return エディタの {@link TextArea}
     */
    public TextArea getTextArea() {
        return textArea;
    }

    /**
     * エディタ内の現在のテキスト全文を取得する。
     *
     * @return 現在のテキスト
     */
    public String getEditorText() {
        return textArea.getText();
    }

    /**
     * エディタ内のテキストを指定文字列で置き換える。
     *
     * @param text 設定するテキスト
     */
    public void setEditorText(String text) {
        textArea.setText(text);
    }

    /**
     * エディタ内のテキストを消去する。
     */
    public void clearEditorText() {
        textArea.clear();
    }

    /**
     * {@link TextArea} の自動折り返し設定を更新する。
     *
     * @param enabled 折り返しを有効にする場合は {@code true}
     */
    public void setWrapText(boolean enabled) {
        textArea.setWrapText(enabled);
    }

    /**
     * プレビュー用の {@link WebView} にHTMLコンテンツを読み込んで描画を更新する。
     *
     * @param htmlContent 表示するHTML文字列
     */
    public void updatePreviewContent(String htmlContent) {
        webView.getEngine().loadContent(htmlContent);
    }

    /**
     * ドキュメント名と未保存状態に応じてウィンドウタイトルを更新する。
     *
     * @param documentName 表示するドキュメント名（ファイル名または「無題」）
     * @param isModified   未保存の変更がある場合は {@code true}
     */
    public void updateWindowTitle(String documentName, boolean isModified) {
        String title = documentName;
        if (isModified) {
            title += " *";
        }
        primaryStage.setTitle(title + WINDOW_TITLE_SUFFIX);
    }

    /**
     * ウィンドウクローズ要求イベント（×ボタン押下相当）を発行する。
     */
    public void fireCloseRequest() {
        // ×ボタン押下と同じWindowEventを発火させることで終了確認ロジックを1箇所に統一する
        primaryStage.fireEvent(
            new WindowEvent(primaryStage, WindowEvent.WINDOW_CLOSE_REQUEST)
        );
    }

    /**
     * ファイルを開くダイアログを表示し、選択されたファイルを返す。
     *
     * @return 選択された {@link File}（キャンセル時は {@code null}）
     */
    public File showOpenFileDialog() {
        return fileChooser.showOpenDialog(primaryStage);
    }

    /**
     * 名前を付けて保存ダイアログを表示し、選択されたファイルを返す。
     *
     * @return 保存先の {@link File}（キャンセル時は {@code null}）
     */
    public File showSaveFileDialog() {
        return fileChooser.showSaveDialog(primaryStage);
    }

    /**
     * 未保存変更の保存・破棄・キャンセルを確認するダイアログを表示する。
     *
     * @return ユーザーの選択結果を表す {@link SaveConfirmationChoice}
     */
    public SaveConfirmationChoice showSaveConfirmationDialog() {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("変更の確認");
        alert.setHeaderText("保存されていない変更があります。");
        alert.setContentText("変更を保存しますか？");

        ButtonType buttonTypeSave = new ButtonType("はい (保存)");
        ButtonType buttonTypeNo = new ButtonType("いいえ (破棄)");
        ButtonType buttonTypeCancel = new ButtonType("キャンセル");

        alert.getButtonTypes().setAll(buttonTypeSave, buttonTypeNo, buttonTypeCancel);

        Optional<ButtonType> result = alert.showAndWait();

        // JavaFX固有のButtonTypeをビュー内部に閉じ込め、コントローラには業務的な選択肢のみを返す
        if (result.isPresent() && result.get() == buttonTypeSave) {
            return SaveConfirmationChoice.SAVE;
        } else if (result.isPresent() && result.get() == buttonTypeNo) {
            return SaveConfirmationChoice.DISCARD;
        } else {
            return SaveConfirmationChoice.CANCEL;
        }
    }

    /**
     * エラーダイアログを表示する。
     *
     * @param title   ダイアログのタイトル
     * @param message ユーザーに表示するエラーメッセージ
     */
    public void showErrorDialog(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
