package application;

import javafx.application.Application;
import javafx.stage.Stage;

// JavaFXアプリケーションの起動と各コンポーネントの組み立てを行うメインクラス。

/**
 * Markdownエディタアプリケーションのエントリーポイント。
 * <p>
 * HTML変換を担う {@link MarkdownRenderer}、UI描画を担う {@link EditorView}、
 * およびイベント・状態管理を担う {@link EditorController} を生成して結合し、メインウィンドウを起動する。
 * </p>
 */
public class MarkdownEditor extends Application {

    /**
     * JavaFXアプリケーションの起動時に各コンポーネントを初期化してウィンドウを表示する。
     *
     * @param primaryStage アプリケーションのプライマリステージ
     */
    @Override
    public void start(Stage primaryStage) {
        // 変換・描画・制御の責務ごとにクラスを分離し、初期設定値もコントローラとビューで共有して整合性を保つ
        MarkdownRenderer markdownRenderer = new MarkdownRenderer();
        EditorView view = new EditorView(
            primaryStage,
            EditorController.DEFAULT_WRAP_TEXT_ENABLED,
            EditorController.DEFAULT_OVERWRITE_MODE
        );
        EditorController controller = new EditorController(view, markdownRenderer);

        controller.initialize();

        primaryStage.setScene(view.createScene());
        primaryStage.show();
    }

    /**
     * アプリケーションを起動するメインメソッド。
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        launch(args);
    }
}
