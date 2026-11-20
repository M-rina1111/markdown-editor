package application;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import javafx.stage.WindowEvent;

// エディタの状態管理・ファイル入出力・各種ユーザーイベント制御を担当するコントローラクラス。

/**
 * {@link EditorView} と {@link MarkdownRenderer} を仲介し、
 * アプリケーション全体のイベント処理と編集状態を管理するコントローラクラス。
 * <p>
 * ファイルの新規作成・読み込み・保存、未保存変更の破棄確認、テキスト変更時のプレビュー同期、
 * 折り返し設定切替、および上書き入力モードのキーイベント制御を担当する。
 * </p>
 */
public class EditorController {

    /** テキスト折り返し設定の初期値 */
    public static final boolean DEFAULT_WRAP_TEXT_ENABLED = true;

    /** 上書きモード設定の初期値 */
    public static final boolean DEFAULT_OVERWRITE_MODE = false;

    private static final String UNTITLED = "無題";

    private final EditorView view;
    private final MarkdownRenderer markdownRenderer;

    // 新規作成直後は未保存ファイルを示すためnullで保持する
    private File currentFile = null;
    private boolean isModified = false;
    private boolean isOverwriteMode = DEFAULT_OVERWRITE_MODE;
    private boolean isWrapTextEnabled = DEFAULT_WRAP_TEXT_ENABLED;

    /**
     * ビューとレンダラを受け取りコントローラを生成する。
     *
     * @param view             UI表示およびダイアログ操作を担う {@link EditorView}
     * @param markdownRenderer Markdown変換を担う {@link MarkdownRenderer}
     */
    public EditorController(EditorView view, MarkdownRenderer markdownRenderer) {
        this.view = view;
        this.markdownRenderer = markdownRenderer;
    }

    /**
     * ビューの各UIイベントへハンドラを接続し、起動直後の初期ドキュメント状態を設定する。
     */
    public void initialize() {
        view.setOnFileNew(this::handleFileNew);
        view.setOnFileOpen(this::handleFileOpen);
        view.setOnFileSave(this::handleFileSave);
        view.setOnFileSaveAs(this::handleFileSaveAs);
        view.setOnFileExit(this::handleFileExit);

        view.setOnWrapTextChanged(this::handleWrapTextChanged);
        view.setOnOverwriteModeChanged(this::handleOverwriteModeChanged);

        view.addTextChangeListener(this::handleTextChanged);
        view.addKeyTypedFilter(this::handleKeyTyped);
        view.setOnWindowCloseRequest(this::handleWindowClose);

        // 起動直後は「新規作成」状態でタイトルとプレビューを初期化する
        handleFileNew();
    }

    /**
     * 新規ドキュメント作成処理を実行する。
     */
    private void handleFileNew() {
        if (!confirmCloseWithoutSaving()) {
            return;
        }

        view.clearEditorText();
        currentFile = null;
        isModified = false;
        updateWindowTitle();
        // 空→空のclear()ではtextPropertyリスナーが発火しないため、初期CSSをWebViewへ必ず適用する
        updatePreview();
    }

    /**
     * ファイル選択ダイアログを表示し、選択されたMarkdownファイルを読み込んでエディタへ反映する。
     */
    private void handleFileOpen() {
        if (!confirmCloseWithoutSaving()) {
            return;
        }

        File file = view.showOpenFileDialog();
        if (file != null) {
            try {
                String content = Files.readString(file.toPath());
                view.setEditorText(content);
                // setEditorText()の発火でリスナーがisModifiedをtrueにするため、読み込み直後に未変更状態へ確定させる
                currentFile = file;
                isModified = false;
                updateWindowTitle();
            } catch (IOException ex) {
                view.showErrorDialog("読み込みエラー", "ファイルの読み込みに失敗しました。");
            }
        }
    }

    /**
     * 上書き保存処理を実行する。
     * <p>
     * まだ保存先ファイルが紐付いていない新規ドキュメントの場合は「名前を付けて保存」へ委譲する。
     * </p>
     */
    private void handleFileSave() {
        if (currentFile == null) {
            handleFileSaveAs();
        } else {
            saveContentToFile(currentFile);
        }
    }

    /**
     * 名前を付けて保存ダイアログを表示し、指定されたファイルへテキストを保存する。
     */
    private void handleFileSaveAs() {
        File file = view.showSaveFileDialog();
        if (file != null) {
            saveContentToFile(file);
        }
    }

    /**
     * 指定されたファイルへ現在のテキスト内容を書き込み、保存成功時のみ編集状態を更新する。
     *
     * @param file 書き込み先の {@link File}
     */
    private void saveContentToFile(File file) {
        try {
            String content = view.getEditorText();
            Files.writeString(file.toPath(), content);
            // タイトル更新前にcurrentFileを確定させ、かつI/O失敗時に誤って保存先が切り替わるのを防ぐため成功後に更新する
            currentFile = file;
            isModified = false;
            updateWindowTitle();
        } catch (IOException ex) {
            view.showErrorDialog("保存エラー", "ファイルの保存に失敗しました。");
        }
    }

    /**
     * 「終了」メニュー選択時の処理としてウィンドウクローズ要求を発行する。
     */
    private void handleFileExit() {
        view.fireCloseRequest();
    }

    /**
     * ウィンドウクローズ要求を受け取った際に未保存変更の確認を行い、キャンセル時は終了を中断する。
     *
     * @param event ウィンドウクローズイベント
     */
    private void handleWindowClose(WindowEvent event) {
        if (!confirmCloseWithoutSaving()) {
            event.consume();
        }
    }

    /**
     * 未保存の変更がある場合に保存・破棄・キャンセルを確認し、続行可否を返す。
     *
     * @return 処理を続行してよい場合は {@code true}、キャンセル時は {@code false}
     */
    private boolean confirmCloseWithoutSaving() {
        if (!isModified) {
            return true;
        }

        return switch (view.showSaveConfirmationDialog()) {
            case SAVE -> {
                handleFileSave();
                // 保存ダイアログのキャンセルや書き込み失敗時はisModifiedがtrueのまま残るため、保存完了時のみ続行を許可する
                yield !isModified;
            }
            case DISCARD -> true;
            case CANCEL -> false;
        };
    }

    /**
     * エディタテキスト変更時にプレビューを更新し、未保存フラグを必要に応じて立てる。
     */
    private void handleTextChanged() {
        // 変換処理をupdatePreview()へ集約し、二重パースを防ぎつつ未保存状態を更新する
        updatePreview();
        if (!isModified) {
            isModified = true;
            updateWindowTitle();
        }
    }

    /**
     * テキスト折り返し設定の変更を受けてエディタとプレビュー双方へ反映する。
     *
     * @param enabled 折り返しを有効にする場合は {@code true}
     */
    private void handleWrapTextChanged(boolean enabled) {
        // 折り返し設定の変更がエディタ入力欄とWebViewプレビューで食い違わないよう一括で反映する
        isWrapTextEnabled = enabled;
        view.setWrapText(enabled);
        updatePreview();
    }

    /**
     * 挿入/上書きモードの状態を更新する。
     *
     * @param enabled 上書きモードを有効にする場合は {@code true}
     */
    private void handleOverwriteModeChanged(boolean enabled) {
        isOverwriteMode = enabled;
    }

    /**
     * 上書きモードが有効な場合にキー入力イベントを横取りし、カーソル直後の1文字を置換する。
     * <p>
     * 修飾キー併用時、範囲選択中、制御文字（Deleteや改行など）の入力時、
     * およびカーソル位置が文末または改行直前の場合は通常の挿入／編集動作に委ねる。
     * </p>
     *
     * @param event キータイプイベント
     */
    private void handleKeyTyped(KeyEvent event) {
        if (!isOverwriteMode) {
            return;
        }

        // Ctrl/Alt/Meta等のショートカット入力は文字上書きの対象外として標準動作に任せる
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown() || event.isShortcutDown()) {
            return;
        }

        TextArea textArea = view.getTextArea();

        // テキストが範囲選択されている場合は1文字上書きではなく選択範囲の置換を行うべきため標準動作に任せる
        if (textArea.getSelection().getLength() > 0) {
            return;
        }

        String typedChar = event.getCharacter();
        // Delete(\u007F)やEnter、TabなどのISO制御文字をすべて除外するためCharacter.isISOControlで判定する
        if (typedChar == null || typedChar.isEmpty() || Character.isISOControl(typedChar.codePointAt(0))) {
            return;
        }

        int caretPos = textArea.getCaretPosition();
        int textLen = textArea.getLength();

        if (caretPos < textLen) {
            // サロゲートペアを分断せずに1文字（1コードポイント）単位で上書きするため最大2char分を取得して判定する
            String nextSlice = textArea.getText(caretPos, Math.min(caretPos + 2, textLen));
            int nextCodePoint = nextSlice.codePointAt(0);

            if (nextCodePoint != '\n' && nextCodePoint != '\r') {
                int charCount = Character.charCount(nextCodePoint);
                textArea.replaceText(caretPos, caretPos + charCount, typedChar);
                event.consume();
            }
        }
    }

    /**
     * 現在のMarkdownテキストをHTMLへ変換し、ビューのプレビューを更新する。
     */
    private void updatePreview() {
        String html = markdownRenderer.renderToHtml(view.getEditorText(), isWrapTextEnabled);
        view.updatePreviewContent(html);
    }

    /**
     * 現在のファイル名（または無題）と編集状態をもとにウィンドウタイトルを更新する。
     */
    private void updateWindowTitle() {
        String documentName = (currentFile == null) ? UNTITLED : currentFile.getName();
        view.updateWindowTitle(documentName, isModified);
    }
}
