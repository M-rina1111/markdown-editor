package application;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

// CommonMarkを用いてMarkdown文字列をプレビュー用のスタイル付きHTMLへ変換するクラス。

/**
 * CommonMarkによるMarkdownのパースおよびHTMLレンダリングを担当するクラス。
 * <p>
 * JavaFXのUIコンポーネントには依存せず、受け取ったMarkdownテキストをHTMLへ変換し、
 * 折り返し設定に応じたCSSスタイルを付与したプレビュー用HTMLを生成する。
 * </p>
 */
public class MarkdownRenderer {

    // 初期化後は変更しない不変コレクションとして拡張機能を定義し、パーサとレンダラで同一構成を共有する
    private final List<Extension> extensions = List.of(
        TablesExtension.create(),
        TaskListItemsExtension.create(),
        StrikethroughExtension.create(),
        AutolinkExtension.create(),
        InsExtension.create()
    );

    private final Parser parser = Parser.builder()
        .extensions(extensions)
        .build();

    private final HtmlRenderer renderer = HtmlRenderer.builder()
        .extensions(extensions)
        .build();

    /**
     * 指定されたMarkdown文字列をHTMLへ変換し、プレビュー用CSSを付与して返す。
     *
     * @param markdownText      変換対象のMarkdownテキスト（{@code null} の場合は空文字として扱う）
     * @param isWrapTextEnabled プレビュー上でテキストを折り返す場合は {@code true}
     * @return {@code <style>} タグ付きのHTML文字列
     */
    public String renderToHtml(String markdownText, boolean isWrapTextEnabled) {
        // TextAreaの値がnullを返すケースや外部からのnull入力でもNullPointerExceptionを防ぐため空文字へ正規化する
        String safeText = (markdownText == null) ? "" : markdownText;

        Node document = parser.parse(safeText);
        String html = renderer.render(document);

        // エディタ側の折り返し設定に合わせてコードブロック(<pre>)を含むHTML全体の折り返し挙動を切り替える
        String wrapCss;
        if (isWrapTextEnabled) {
            wrapCss = "body { word-wrap: break-word; } pre { white-space: pre-wrap; }";
        } else {
            wrapCss = "body { white-space: nowrap; } pre { white-space: pre; }";
        }

        String css = """
            <style>
              body {
                font-family: 'Yu Gothic UI', sans-serif;
                font-size: 16px;
              }
              pre, code {
                font-family: 'Consolas', 'Menlo', monospace;
                background-color: #f5f5f5;
                border-radius: 4px;
                padding: 2px 4px;
              }
              pre {
                padding: 10px;
              }
              %s
            </style>
            """.formatted(wrapCss);

        return css + html;
    }
}
