package com.egg.codeeditor.ui.components

object FileTypeManager {
    private val dynamicRegistry = mutableListOf<FileType>()

    fun register(fileType: FileType) {
        if (!dynamicRegistry.contains(fileType)) dynamicRegistry.add(fileType)
    }

    fun unregister(fileType: FileType) {
        dynamicRegistry.remove(fileType)
    }

    fun allTypes(): List<FileType> = BuiltinFileType.entries + dynamicRegistry

    fun fromFileName(name: String): FileType {
        val normalized = name.lowercase()
        val fileExt = normalized.substringAfterLast('.', "")
        return allTypes().firstOrNull { it.names != null && normalized in it.names!! }
            ?: fromExtension(fileExt)
    }

    fun fromExtension(ext: String): FileType {
        val normalized = ext.lowercase().removePrefix(".")
        return allTypes().firstOrNull { normalized in it.extensions } ?: BuiltinFileType.UNKNOWN
    }
}

enum class BuiltinFileType(
    override val extensions: List<String>,
    override val names: List<String>? = null,
    override val iconName: String?,
    override val iconOverride: Map<String, String>? = null,
    override val title: String,
) : FileType {
    JAVASCRIPT(listOf("js","mjs","cjs","jscsrc","jshintrc","javascript"), iconName = "ic_ft_js", title = "JavaScript"),
    TYPESCRIPT(listOf("ts","mts","cts","typescript"), iconName = "ic_ft_ts", title = "TypeScript"),
    JSX(listOf("jsx"), iconName = "ic_ft_react", title = "JavaScript JSX"),
    TSX(listOf("tsx"), iconName = "ic_ft_react", title = "TypeScript JSX"),
    HTML(listOf("html","htm","xhtml","xht"), iconName = "ic_ft_html", title = "HTML"),
    HTMX(listOf("htmx"), iconName = "ic_ft_html", title = "HTMX"),
    CSS(listOf("css"), iconName = "ic_ft_css", title = "CSS"),
    SCSS(listOf("scss","sass"), iconName = "ic_ft_sass", title = "SCSS"),
    LESS(listOf("less"), iconName = "ic_ft_less", title = "Less"),
    JSON(listOf("json","jsonl","jsonc"), iconName = "ic_ft_json", title = "JSON"),
    MARKDOWN(listOf("md","markdown","mdown","mkd","mkdn","mdoc","mdtext","mdtxt","mdwn"), iconName = "ic_ft_markdown", title = "Markdown"),
    XML(listOf("xml","xaml","dtd","plist","ascx","csproj","wxi","wxl","wxs","svg"), iconName = "ic_ft_xml", title = "XML"),
    YAML(listOf("yaml","yml","eyaml","eyml","cff"), iconName = "ic_ft_yaml", title = "YAML"),
    PYTHON(listOf("py","pyi"), iconName = "ic_ft_python", title = "Python"),
    JAVA(listOf("java","jav","bsh"), iconName = "ic_ft_java", title = "Java"),
    GROOVY(listOf("gsh","groovy","gradle","gvy","gy"), iconName = "ic_ft_groovy", iconOverride = mapOf("gradle" to "ic_ft_gradle"), title = "Groovy"),
    C(listOf("c"), iconName = "ic_ft_c", title = "C"),
    CPP(listOf("cpp","cxx","cc","c++","h","hpp","hh","hxx","h++"), iconName = "ic_ft_cpp", title = "C++"),
    CSHARP(listOf("cs","csx"), iconName = "ic_ft_csharp", title = "C#"),
    RUBY(listOf("rb","erb","gemspec"), iconName = "ic_ft_ruby", title = "Ruby"),
    LUA(listOf("lua","luau"), iconName = "ic_ft_lua", title = "Lua"),
    GO(listOf("go"), iconName = "ic_ft_go", title = "Go"),
    PHP(listOf("php"), iconName = "ic_ft_php", title = "PHP"),
    RUST(listOf("rs"), iconName = "ic_ft_rust", title = "Rust"),
    PASCAL(listOf("p","pas"), iconName = null, title = "Pascal"),
    ZIG(listOf("zig","zon"), iconName = "ic_ft_zig", title = "Zig"),
    NIM(listOf("nim"), iconName = "ic_ft_nim", title = "Nim"),
    SWIFT(listOf("swift"), iconName = "ic_ft_swift", title = "Swift"),
    DART(listOf("dart"), iconName = "ic_ft_dart", title = "Dart"),
    ROCQ(listOf("v","coq"), iconName = null, title = "Rocq (Coq)"),
    KOTLIN(listOf("kt","kts"), iconName = "ic_ft_kotlin", title = "Kotlin"),
    LISP(listOf("lisp","clisp"), iconName = "ic_ft_lisp", title = "Lisp"),
    SHELL(listOf("sh","bash","bash_login","bash_logout","bash_profile","bashrc","profile","rhistory","rprofile","zsh","zlogin","zlogout","zprofile","zshenv","zshrc","fish","ksh"), iconName = "ic_ft_shell", title = "Shell script"),
    WINDOWS_SHELL(listOf("cmd","bat"), iconName = "ic_ft_shell", title = "Batch"),
    POWERSHELL(listOf("ps1","psm1","psd1"), iconName = "ic_ft_powershell", title = "PowerShell"),
    SMALI(listOf("smali"), iconName = null, title = "Smali"),
    ASSEMBLY(listOf("asm","s","S"), iconName = null, title = "Assembly"),
    CMAKE(emptyList(), names = listOf("cmakelists.txt"), iconName = "ic_ft_cmake", title = "CMake"),
    R(listOf("r"), iconName = "ic_ft_r", title = "R"),
    NIX(listOf("nix"), iconName = "ic_ft_nix", title = "Nix"),
    SQL(listOf("sql","dsql","sqllite"), iconName = "ic_ft_sql", title = "SQL"),
    TOML(listOf("toml"), iconName = "ic_ft_toml", title = "TOML"),
    INI(listOf("ini"), iconName = "ic_ft_settings", title = "INI"),
    PROPERTIES(listOf("properties","cfg","conf","config","editorconfig","gitconfig","gitmodules","gitattributes"), iconName = "ic_ft_settings", iconOverride = mapOf("gitmodules" to "ic_ft_git", "gitattributes" to "ic_ft_git", "gitconfig" to "ic_ft_git"), title = "Properties"),
    IGNORE(listOf("gitignore","gitignore_global","gitkeep","git-blame-ignore-revs"), iconName = "ic_ft_git", title = "Ignore"),
    DIFF(listOf("diff","patch","rej"), iconName = "ic_ft_diff", title = "Diff"),
    TEXT(listOf("txt"), iconName = "ic_ft_text", title = "Plain text"),
    LOG(listOf("log"), iconName = null, title = "Log"),
    LATEX(listOf("latex","tex","ltx"), iconName = "ic_ft_latex", title = "LaTeX"),
    IMAGE(listOf("jpg","jpeg","png","gif","bmp","tiff","webp","ico","heic","heif","avif"), iconName = "ic_ft_image", title = "Image"),
    AUDIO(listOf("mp3","wav","flac","ogg","aac","m4a","wma","opus"), iconName = "ic_ft_audio", title = "Audio"),
    VIDEO(listOf("mp4","avi","mov","mkv","webm"), iconName = "ic_ft_video", title = "Video"),
    ARCHIVE(listOf("zip","rar","7z","tar","gz","bz2","xy"), iconName = "ic_ft_archive", title = "Archive"),
    EXECUTABLE(listOf("exe","dll","so","dylib","bin"), iconName = null, title = "Executable"),
    APK(listOf("apk","xapk","apks"), iconName = "ic_ft_apk", title = "APK"),
    XED_PACKAGE(listOf("xed"), iconName = "ic_ft_xed", title = "Xed Package"),
    UNKNOWN(emptyList(), iconName = null, title = "Unknown"),
}
