module markdown_editor { 
    // JavaFXのライブラリを使うことを宣言
    requires javafx.controls;
    requires javafx.web;
    requires javafx.graphics;
    
    // CommonMarkライブラリを使うことを宣言
    requires org.commonmark;
    requires org.commonmark.ext.gfm.tables;
    requires org.commonmark.ext.task.list.items;
    requires org.commonmark.ext.gfm.strikethrough;
    requires org.commonmark.ext.autolink;
    requires org.commonmark.ext.ins;

    // 'application' パッケージをJavaFXに公開する
    opens application to javafx.graphics;
}