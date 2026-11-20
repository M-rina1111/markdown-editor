package application;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Optional; // ★ 警告ダイアログの結果判定用

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert; // ★ 警告ダイアログ
import javafx.scene.control.Alert.AlertType; // ★
import javafx.scene.control.ButtonType; // ★
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem; // ★ メニューの区切り線
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent; // ★ 閉じるイベントの検知用

public class MarkdownEditor extends Application {
	// 1. 使用する拡張機能のリストを作成
	private List<Extension> extensions = Arrays.asList(
	        TablesExtension.create(),         // テーブル (既存)
	        TaskListItemsExtension.create(), // タスクリスト (既存)
	        StrikethroughExtension.create(), // 打ち消し線 (今回追加)
	        AutolinkExtension.create(),      // オートリンク (今回追加)
	        InsExtension.create()            // 下線 (今回追加)
	    );

    private Parser parser = Parser.builder()
            .extensions(extensions)
            .build();
    private HtmlRenderer renderer = HtmlRenderer.builder()
            .extensions(extensions)
            .build();
    
    private TextArea textArea;
    private WebView webView;
    private Stage primaryStage; // ★ 閉じる処理などでStageを使うためフィールド化
    private FileChooser fileChooser = new FileChooser();

    // ★ --- 状態管理のためのフィールド ---
    
    // 現在開いているファイル（新規作成時は null）
    private File currentFile = null; 
    // 最後に保存してから変更されたか (dirty flag)
    private boolean isModified = false; 
    // ウィンドウタイトル用の定数
    private final String UNTITLED = "無題";
    
 // ★ 挿入/上書きモードの状態 (false = 挿入, true = 上書き)
    private boolean isOverwriteMode = false;
    
    private boolean isWrapTextEnabled = true; // デフォルトはON
    
    // ★ --------------------------------

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage; // ★ Stageをフィールドに保存
        
        BorderPane root = new BorderPane();

        // --- メニューバーの作成 (拡張) ---
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("ファイル");
        
        MenuItem newItem = new MenuItem("新規作成"); // ★ 追加
        newItem.setOnAction(e -> handleFileNew());
        
        MenuItem openItem = new MenuItem("開く...");
        openItem.setOnAction(e -> handleFileOpen()); 
        
        MenuItem saveItem = new MenuItem("上書き保存"); // ★ 追加
        saveItem.setOnAction(e -> handleFileSave());

        MenuItem saveAsItem = new MenuItem("名前を付けて保存...");
        saveAsItem.setOnAction(e -> handleFileSaveAs());
        
        MenuItem exitItem = new MenuItem("終了"); // ★ 追加
        exitItem.setOnAction(e -> handleFileExit());
        
        Menu settingsMenu = new Menu("設定");

        // --- テキスト折り返し ---
        ToggleGroup wrapToggleGroup = new ToggleGroup();

        RadioMenuItem noWrapItem = new RadioMenuItem("折り返さない");
        noWrapItem.setToggleGroup(wrapToggleGroup);
        noWrapItem.setOnAction(e -> { // ★ ラムダをブロックに変更
            textArea.setWrapText(false);
            isWrapTextEnabled = false;
            updateWebViewContent(); // ★ プレビューを強制更新
        });

        RadioMenuItem wrapItem = new RadioMenuItem("右端で折り返す");
        wrapItem.setToggleGroup(wrapToggleGroup);
        wrapItem.setOnAction(e -> { // ★ ラムダをブロックに変更
            textArea.setWrapText(true);
            isWrapTextEnabled = true;
            updateWebViewContent(); // ★ プレビューを強制更新
        });
        wrapItem.setSelected(true); // デフォルトでON

        settingsMenu.getItems().addAll(noWrapItem, wrapItem, new SeparatorMenuItem());

        // --- 挿入/上書き切り替え ---
        CheckMenuItem overwriteItem = new CheckMenuItem("挿入/上書き切り替え");
        overwriteItem.setSelected(isOverwriteMode); // デフォルト(false)を反映
        overwriteItem.setOnAction(e -> {
            isOverwriteMode = overwriteItem.isSelected(); // 状態を更新
        });
        
        settingsMenu.getItems().add(overwriteItem);
        
        // ★ メニューバーに「設定」を追加
        menuBar.getMenus().add(settingsMenu);
        // ★★★ 「設定」メニュー (ここまで) ★★★
        
        root.setTop(menuBar);

        // ★ メニューにアイテムを追加 (区切り線も)
        fileMenu.getItems().addAll(
                newItem, openItem, 
                new SeparatorMenuItem(), // 区切り線
                saveItem, saveAsItem,
                new SeparatorMenuItem(), // 区切り線
                exitItem);
        menuBar.getMenus().add(fileMenu);
        root.setTop(menuBar);
        // --- メニューバー (ここまで) ---

        textArea = new TextArea();
        webView = new WebView();
        
        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(textArea, webView);
        splitPane.setDividerPositions(0.3);
        root.setCenter(splitPane);
        
        textArea.setPromptText("ここにMarkdownを入力...");
        textArea.setStyle("-fx-font-family: 'Yu Gothic UI'; -fx-font-size: 15px;");
        
        textArea.setWrapText(true);
        root.setCenter(splitPane);
        
        // --- 連携ロジック (isModifiedフラグの更新を追加) ---
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            // 1. Markdown -> HTML 変換 (変更なし)
            Node document = parser.parse(newValue); 
            String html = renderer.render(document); 
         // 1. ★★★ プレビューを更新するロジックを呼び出す
            updateWebViewContent();
            
            // 2. ★ 変更フラグを立てる (これはキー入力時にのみ行う)
            if (!isModified) {
                isModified = true;
                updateWindowTitle(); 
            }
        });
        
        textArea.setOnKeyTyped(event -> handleKeyTyped(event));
        
        Scene scene = new Scene(root, 800, 600); 
        
        // ★ 閉じるボタンの動作を上書き
        primaryStage.setOnCloseRequest(event -> handleWindowClose(event));
        
        primaryStage.setScene(scene);
        
        // ★ FileChooserの初期設定 (メソッド呼び出しに変更)
        configureFileChooser();
        
        // ★ 起動直後は「新規作成」状態にする
        handleFileNew(); 
        
        primaryStage.show();
    }
    
    // --- FileChooserの初期設定 (変更なし) ---
    private void configureFileChooser() {
        fileChooser.setTitle("Markdownファイルを選択");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Markdown ファイル", "*.md", "*.MD", "*.markdown"),
            new FileChooser.ExtensionFilter("すべてのファイル", "*.*")
        );
    }
    
    // ★ ウィンドウのタイトルを更新する
    private void updateWindowTitle() {
        String title;
        if (currentFile == null) {
            title = UNTITLED; // "無題"
        } else {
            title = currentFile.getName(); // "README.md"
        }
        
        if (isModified) {
            title += " *"; // "無題 *" や "README.md *"
        }
        
        primaryStage.setTitle(title + " - Markdownエディタ");
    }

    // ★ 「新規作成」の処理
    private void handleFileNew() {
        // もし変更があれば、先に保存するか確認する
        if (!confirmCloseWithoutSaving()) {
            return; // キャンセルされた
        }
        
        textArea.clear(); // テキストエリアを空に
        currentFile = null; // 現在のファイル参照を消す
        isModified = false; // 変更フラグをリセット
        updateWindowTitle(); // タイトルをリセット
    }

    // ★ 「開く」の処理 (状態管理を追加)
    private void handleFileOpen() {
        // もし変更があれば、先に保存するか確認する
        if (!confirmCloseWithoutSaving()) {
            return; // キャンセルされた
        }

        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                textArea.setText(content);
                currentFile = file; // ★ 現在開いているファイルとして記憶
                isModified = false;  // ★ 読み込んだ直後は「変更なし」
                updateWindowTitle(); // ★ タイトルをファイル名に更新
            } catch (IOException ex) {
                showErrorDialog("読み込みエラー", "ファイルの読み込みに失敗しました。");
            }
        }
    }
    
    // ★ 「上書き保存」の処理
    private void handleFileSave() {
        if (currentFile == null) {
            // まだ一度も保存されていない（＝無題）なら、「名前を付けて保存」と同じ
            handleFileSaveAs();
        } else {
            // すでにファイル名があるなら、ダイアログなしで上書き
            saveContentToFile(currentFile);
        }
    }

    // ★ 「名前を付けて保存」の処理 (状態管理を追加)
    private void handleFileSaveAs() {
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            saveContentToFile(file);
            currentFile = file; // ★ 保存したファイルを「現在のファイル」として記憶
        }
    }
    
    // ★ ファイルへの実際の書き込み処理（共通化）
    private void saveContentToFile(File file) {
        try {
            String content = textArea.getText();
            Files.writeString(file.toPath(), content);
            isModified = false; // ★ 保存したので「変更なし」状態に戻す
            updateWindowTitle(); // ★ タイトルから "*" を消す
        } catch (IOException ex) {
            showErrorDialog("保存エラー", "ファイルの保存に失敗しました。");
        }
    }
    
    // ★ 「終了」メニューの処理
    private void handleFileExit() {
        // ウィンドウを閉じる処理（×ボタンと同じ）を呼び出す
        // これにより、自動的に confirmCloseWithoutSaving() が呼ばれる
        primaryStage.fireEvent(
            new WindowEvent(primaryStage, WindowEvent.WINDOW_CLOSE_REQUEST)
        );
    }
    
    // ★ ウィンドウが閉じられる時（×ボタンや終了メニュー）の処理
    private void handleWindowClose(WindowEvent event) {
        if (!confirmCloseWithoutSaving()) {
            // ユーザーが「キャンセル」を選んだ場合
            event.consume(); // ★ 閉じるイベントを「消費」＝キャンセルする
        }
        // 「はい」か「いいえ」なら、そのまま閉じる（Platform.exit()は不要）
    }

    /**
     * ★ 保存せずに閉じてよいか確認する (最重要ロジック)
     * @return true = 閉じてもOK, false = キャンセルされた
     */
    private boolean confirmCloseWithoutSaving() {
        if (!isModified) {
            return true; // 変更なし。そのまま閉じてOK
        }

        // 変更がある場合、警告ダイアログを出す
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("変更の確認");
        alert.setHeaderText("保存されていない変更があります。");
        alert.setContentText("変更を保存しますか？");

        // 
        ButtonType buttonTypeSave = new ButtonType("はい (保存)");
        ButtonType buttonTypeNo = new ButtonType("いいえ (破棄)");
        ButtonType buttonTypeCancel = new ButtonType("キャンセル");

        alert.getButtonTypes().setAll(buttonTypeSave, buttonTypeNo, buttonTypeCancel);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == buttonTypeSave) {
            // 「はい (保存)」
            handleFileSave(); // 保存処理
            // もし保存が（キャンセルなどで）失敗したら isModified は true のまま
            return !isModified; // 保存が成功したら true (閉じてOK)
            
        } else if (result.isPresent() && result.get() == buttonTypeNo) {
            // 「いいえ (破棄)」
            return true; // 変更を破棄してOK
            
        } else {
            // 「キャンセル」またはダイアログが閉じられた
            return false; // 閉じるのをキャンセル
        }
    }
    
    // ★ エラーダイアログ表示（共通）
    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * ★★★ 挿入/上書きモードを処理するキーイベントハンドラ ★★★
     */
    private void handleKeyTyped(KeyEvent event) {
        // 挿入モード(false)なら、何もしない（デフォルトの動作）
        if (!isOverwriteMode) {
            return;
        }

        // 入力された文字（event.getCharacter()）を取得
        String typedChar = event.getCharacter();

        // 以下の場合は「上書き」の対象外とし、デフォルト動作に任せる
        // 1. 入力された文字が空 (例: Backspace, Delete, 矢印キーなど)
        // 2. 入力された文字が制御文字 (例: Enter, Tabなど)
        //    (Backspaceは "\u0008" だが、isEmpty() で通常は弾かれる)
        if (typedChar.isEmpty() || typedChar.charAt(0) < 32) {
             return;
        }

        // --- ここからが「上書き」ロジック ---
        
        int caretPos = textArea.getCaretPosition(); // 現在のカーソル位置
        int textLen = textArea.getLength(); // 全文の長さ

        // カーソルがテキストの末尾でなければ、1文字上書き
        if (caretPos < textLen) {
            // カーソル位置の次の1文字が改行でないかチェック
            char charAfterCaret = textArea.getText(caretPos, caretPos + 1).charAt(0);
            
            // 次が改行でなければ、その1文字を置き換える
            if (charAfterCaret != '\n' && charAfterCaret != '\r') {
                // (caretPos) から (caretPos + 1) の範囲を、入力文字で置き換え
                textArea.replaceText(caretPos, caretPos + 1, typedChar);
                
                // ★ デフォルトの「挿入」動作をキャンセルする
                event.consume(); 
            }
        }
        
        // 次が改行文字、または文末の場合は、
        // event.consume() を呼ばないので、デフォルトの「挿入」動作（＝文字の追加）が行われる
        // これにより、上書きモードでも文末では普通に入力できる
    }
    
    /**
     * ★★★ プレビュー(WebView)の内容を、現在のテキストと設定で更新する ★★★
     */
    private void updateWebViewContent() {
        // 1. 現在のテキストを取得
        String markdownText = textArea.getText();
        
        // 2. Markdown -> HTML 変換 (変更なし)
        Node document = parser.parse(markdownText); 
        String html = renderer.render(document); 
        
        // 3. ★ 折り返し設定に応じてCSSを動的に生成
        String wrapCss;
        if (isWrapTextEnabled) {
            // 折り返し ON
            // <p>などはデフォルトで折り返すが、<pre>（コードブロック）も折り返すように
            wrapCss = "body { word-wrap: break-word; }"
                    + "pre { white-space: pre-wrap; }"; 
        } else {
            // 折り返し OFF
            // body全体を折り返さないようにし、横スクロールを許可
            wrapCss = "body { white-space: nowrap; }"
                    + "pre { white-space: pre; }"; 
        }

        // 4. 既存のスタイルと合体
        String css = "<style>"
                   + "  body {"
                   + "    font-family: 'Yu Gothic UI', sans-serif;"
                   + "    font-size: 16px;"
                   + "  }"
                   + "  pre, code {"
                   + "    font-family: 'Consolas', 'Menlo', monospace;"
                   + "    background-color: #f5f5f5;"
                   + "    border-radius: 4px;"
                   + "    padding: 2px 4px;"
                   + "  }"
                   + "  pre {"
                   + "    padding: 10px;"
                   + "  }"
                   // ★ 動的な折り返しCSSを追加
                   + wrapCss
                   + "</style>";
        
        // 5. WebViewに読み込ませる
        webView.getEngine().loadContent(css + html);
    }
    
    // --- mainメソッド (変更なし) ---
    public static void main(String[] args) {
        launch(args);
    }
}