import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;
import java.util.stream.Stream;

public class Main {
    private static final Logger LOG = Logger.getLogger("app");
    private static ConsoleHandler consoleHandler;
    private static FileHandler fileHandler;
    private static ResourceBundle bundle;
    private static TaskResults sessionResults = new TaskResults();

    private static final String[] LANG_CODES = {"uk", "en"};
    private static final String[] MENU_KEYS = {"menu.reflection", "menu.maxWords", "menu.crypto", "menu.tags",
            "menu.search", "menu.save", "menu.load", "menu.logDemo", "menu.language"};
    private static final Level[] LEVELS = {Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG,
            Level.FINE, Level.FINER, Level.FINEST};

    public static void main(String[] args) {
        setupLogging();
        setLocale(LANG_CODES[0]);
        Scanner scanner = new Scanner(System.in);
        FileManager fm = new FileManager();
        LOG.info("Application started");
        System.out.println(get("app.title"));

        while (true) {
            printMenu();
            String choice = prompt(scanner, get("menu.choose")).trim();
            LOG.fine("Menu choice: " + choice);
            try {
                switch (choice) {
                    case "1":
                        runReflectionTask(scanner);
                        break;
                    case "2": {
                        String file = prompt(scanner, get("maxWords.prompt")).trim();
                        if (file.isEmpty()) break;
                        String line = fm.findMaxWordsLine(file);
                        if (line.isEmpty()) {
                            LOG.warning("No lines with words in " + file);
                            System.out.println(get("maxWords.none"));
                        } else {
                            sessionResults.maxWordsLine = line;
                            int words = line.trim().split("\\s+").length;
                            LOG.info("Max-words line found, words: " + words);
                            System.out.println(get("maxWords.result", words) + "\n" + line);
                        }
                        break;
                    }
                    case "3": {
                        String text = prompt(scanner, get("crypto.textPrompt"));
                        char key = required(scanner, get("crypto.keyPrompt")).charAt(0);
                        fm.writeEncrypted("enc.txt", text, key);
                        LOG.info("Text encrypted to enc.txt, length: " + text.length());
                        System.out.println(get("crypto.encrypted", "enc.txt"));
                        System.out.println(get("crypto.decrypted", fm.readDecrypted("enc.txt", key)));
                        break;
                    }
                    case "4": {
                        String url = required(scanner, get("tags.prompt"));
                        LOG.info("Downloading page: " + url);
                        String html = download(url);
                        sessionResults.tagFrequencies.clear();
                        Matcher m = Pattern.compile("<\\s*([a-zA-Z0-9]+)[^>]*>").matcher(html);
                        while (m.find()) {
                            sessionResults.tagFrequencies.merge(m.group(1).toLowerCase(Locale.ROOT), 1, Integer::sum);
                        }
                        LOG.fine("Characters: " + html.length() + ", unique tags: " + sessionResults.tagFrequencies.size());

                        System.out.println("\n" + get("tags.lexicographic"));
                        sessionResults.tagFrequencies.forEach((k, v) -> System.out.println("<" + k + "> : " + v));

                        System.out.println("\n" + get("tags.byFrequency"));
                        sessionResults.tagFrequencies.entrySet().stream().sorted(Map.Entry.comparingByValue())
                                .forEach(e -> System.out.println("<" + e.getKey() + "> : " + e.getValue()));
                        break;
                    }
                    case "5": {
                        if (sessionResults.tagFrequencies.isEmpty()) {
                            LOG.warning("Tag search requested but there is no tag data");
                            System.out.println(get("search.noData"));
                            break;
                        }
                        String tag = required(scanner, get("search.prompt")).toLowerCase(Locale.ROOT);
                        Integer count = sessionResults.tagFrequencies.get(tag);
                        LOG.fine("Search for tag " + tag + ": " + count);
                        System.out.println(count != null ? get("search.found", tag, count) : get("search.notFound", tag));
                        break;
                    }
                    case "6": {
                        String path = required(scanner, get("save.prompt"));
                        fm.saveResults(path, sessionResults);
                        LOG.info("Results saved to " + path);
                        System.out.println(get("save.ok", path));
                        break;
                    }
                    case "7": {
                        String path = required(scanner, get("load.prompt"));
                        sessionResults = fm.loadResults(path);
                        LOG.info("Results loaded from " + path);
                        System.out.println(get("load.ok", path));
                        System.out.println(get("load.result", sessionResults.tagFrequencies.size()));
                        break;
                    }
                    case "8":
                        logDemo();
                        break;
                    case "9":
                        chooseLanguage(scanner);
                        break;
                    case "0":
                        LOG.info("Application finished");
                        System.out.println(get("app.finished"));
                        return;
                    default:
                        LOG.warning("Invalid menu choice: " + choice);
                        System.out.println(get("menu.invalid"));
                }
            } catch (Exception e) {
                LOG.severe("Error executing task: " + e);
                System.out.println(get("error.generic", e));
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n" + get("menu.header"));
        for (int i = 0; i < MENU_KEYS.length; i++) System.out.println((i + 1) + ". " + get(MENU_KEYS[i]));
        System.out.println("0. " + get("menu.exit"));
    }

    // 1 рефлексія
    private static void runReflectionTask(Scanner scanner) {
        try {
            System.out.println("\n" + get("refl.literal.title"));
            String literal = "Hello, world!";
            System.out.println(get("refl.before") + " " + literal);
            replaceValue(literal, get("refl.works"));
            System.out.println(get("refl.after") + " " + literal);

            String sameLiteral = "Hello, world!";
            System.out.println(get("refl.same") + " " + sameLiteral);

            System.out.println("\n" + get("refl.keyboard.title"));
            String typed = required(scanner, get("refl.enter.string"));
            String newValue = required(scanner, get("refl.enter.new"));
            System.out.println(get("refl.before") + " " + typed);
            replaceValue(typed, newValue);
            System.out.println(get("refl.after") + " " + typed);
            LOG.info("Reflection task completed");
        } catch (ReflectiveOperationException e) {
            LOG.severe("Reflection error: " + e);
            System.out.println(get("refl.error", e));
        } catch (RuntimeException e) {
            LOG.warning("Reflection access denied: " + e.getMessage());
            System.out.println(get("refl.denied", e.getMessage()));
            System.out.println(get("refl.hint"));
        }
    }

    static void replaceValue(String target, String newValue) throws ReflectiveOperationException {
        Field value = String.class.getDeclaredField("value");
        Field coder = String.class.getDeclaredField("coder");
        Field hash = String.class.getDeclaredField("hash");
        Field hashIsZero = String.class.getDeclaredField("hashIsZero");
        for (Field f : new Field[]{value, coder, hash, hashIsZero}) f.setAccessible(true);

        value.set(target, ((byte[]) value.get(newValue)).clone());
        coder.setByte(target, coder.getByte(newValue));
        hash.setInt(target, 0);
        hashIsZero.setBoolean(target, false);
    }

    //2 логування
    private static void setupLogging() {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT [%4$s] %3$s: %5$s%n");

        LOG.setUseParentHandlers(false);
        LOG.setLevel(Level.FINE);//відсікає FINER і FINEST

        consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.WARNING);
        LOG.addHandler(consoleHandler);

        try {
            fileHandler = new FileHandler("app.log", true);
            fileHandler.setLevel(Level.FINE);
            fileHandler.setEncoding("UTF-8");
            fileHandler.setFormatter(new SimpleFormatter());
            LOG.addHandler(fileHandler);
        } catch (IOException e) {
            LOG.warning("Cannot create app.log: " + e.getMessage());
        }
    }

    private static void logDemo() {
        System.out.println("\n" + get("logdemo.title"));
        Level[] phases = {Level.FINE, Level.WARNING};
        for (int p = 0; p < phases.length; p++) {
            LOG.setLevel(phases[p]);
            System.out.println("\n" + get("logdemo.phase", p + 1, phases[p]));
            for (Level level : LEVELS) LOG.log(level, "Demo message, level " + level);

            System.out.println(String.format("%-10s%-13s%-10s%-6s", get("logdemo.col.level"),
                    get("logdemo.col.logger"), get("logdemo.col.console"), get("logdemo.col.file")));
            for (Level level : LEVELS) {
                boolean byLogger = LOG.isLoggable(level);
                boolean toConsole = byLogger && level.intValue() >= consoleHandler.getLevel().intValue();
                boolean toFile = byLogger && fileHandler != null && level.intValue() >= fileHandler.getLevel().intValue();
                System.out.println(String.format("%-10s%-13s%-10s%-6s", level,
                        yesNo(byLogger), yesNo(toConsole), yesNo(toFile)));
            }
        }
        LOG.setLevel(Level.FINE);
        System.out.println("\n" + get("logdemo.note"));
        System.out.println(get("logdemo.fileHint", "app.log"));
    }

    private static String yesNo(boolean value) {
        return get(value ? "logdemo.yes" : "logdemo.no");
    }

    //завдання 3
    private static void chooseLanguage(Scanner scanner) {
        System.out.println("\n" + get("lang.title"));
        for (int i = 0; i < LANG_CODES.length; i++) {
            Locale l = Locale.forLanguageTag(LANG_CODES[i]);
            System.out.println((i + 1) + ". " + l.getDisplayLanguage(l));
        }
        String input = prompt(scanner, get("lang.prompt")).trim();
        try {
            int index = Integer.parseInt(input) - 1;
            setLocale(LANG_CODES[index]);
            Locale l = Locale.forLanguageTag(LANG_CODES[index]);
            LOG.info("Language changed to " + LANG_CODES[index]);
            System.out.println(get("lang.changed", l.getDisplayLanguage(l)));
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            LOG.warning("Invalid language choice: " + input);
            System.out.println(get("lang.invalid"));
        }
    }

    private static void setLocale(String lang) {
        Locale locale = Locale.forLanguageTag(lang);
        try {
            bundle = ResourceBundle.getBundle("location.messages", locale);
        } catch (MissingResourceException e) {
            try {   // папка resources може бути не в classpath
                bundle = ResourceBundle.getBundle("location.messages", locale,
                        new URLClassLoader(new URL[]{new File("resources").toURI().toURL()}));
            } catch (Exception ex) {
                LOG.severe("Localization not found: " + lang);
            }
        }
    }

    private static String get(String key, Object... args) {
        if (bundle == null) return key;
        String text = bundle.getString(key);
        return args.length == 0 ? text
                : MessageFormat.format(text, Arrays.stream(args).map(String::valueOf).toArray());
    }

    //допоміжні методи
    private static String prompt(Scanner sc, String msg) {
        System.out.print(msg + " ");
        return sc.nextLine();
    }

    private static String required(Scanner sc, String msg) {
        while (true) {
            String input = prompt(sc, msg).trim();
            if (!input.isEmpty()) return input;
            System.out.println(get("value.empty"));
        }
    }

    private static String download(String address) throws IOException {
        URLConnection conn = URI.create(address).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

class TaskResults implements Serializable {
    private static final long serialVersionUID = 1L;
    public String maxWordsLine = "";
    public Map<String, Integer> tagFrequencies = new TreeMap<>();
}

class FileManager {
    public String findMaxWordsLine(String path) throws IOException {
        try (Stream<String> lines = Files.lines(Path.of(path))) {
            return lines.filter(l -> !l.trim().isEmpty())
                        .max(Comparator.comparingInt(l -> l.trim().split("\\s+").length))
                        .orElse("");
        }
    }

    public void writeEncrypted(String path, String text, char key) throws IOException {
        try (CryptoWriter cw = new CryptoWriter(new FileWriter(path, StandardCharsets.UTF_8), key)) {
            cw.write(text);
        }
    }

    public String readDecrypted(String path, char key) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (CryptoReader cr = new CryptoReader(new FileReader(path, StandardCharsets.UTF_8), key)) {
            int c;
            while ((c = cr.read()) != -1) sb.append((char) c);
        }
        return sb.toString();
    }

    public void saveResults(String path, TaskResults res) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(path))) {
            oos.writeObject(res);
        }
    }

    public TaskResults loadResults(String path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path))) {
            return (TaskResults) ois.readObject();
        }
    }
}

class CryptoWriter extends FilterWriter {
    private final char key;

    public CryptoWriter(Writer out, char key) { super(out); this.key = key; }

    public char encrypt(char c) { return (char) (c + key); }

    @Override public void write(int c) throws IOException { super.write(encrypt((char) c)); }

    @Override public void write(char[] cbuf, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) write(cbuf[i]);
    }

    @Override public void write(String str, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) write(str.charAt(i));
    }
}

class CryptoReader extends FilterReader {
    private final char key;

    public CryptoReader(Reader in, char key) { super(in); this.key = key; }

    public char decrypt(char c) { return (char) (c - key); }

    @Override public int read() throws IOException {
        int c = super.read();
        return c == -1 ? -1 : decrypt((char) c);
    }

    @Override public int read(char[] cbuf, int off, int len) throws IOException {
        int r = super.read(cbuf, off, len);
        if (r != -1) for (int i = off; i < off + r; i++) cbuf[i] = decrypt(cbuf[i]);
        return r;
    }
}