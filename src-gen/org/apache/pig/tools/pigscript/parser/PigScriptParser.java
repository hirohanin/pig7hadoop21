/* Generated By:JavaCC: Do not edit this line. PigScriptParser.java */
package org.apache.pig.tools.pigscript.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Stack;
import java.util.List;
import java.util.ArrayList;

import jline.ConsoleReader;

public abstract class PigScriptParser implements PigScriptParserConstants {
        protected boolean mInteractive;
        protected ConsoleReader mConsoleReader;

        public void setInteractive(boolean interactive)
        {
                mInteractive = interactive;
                token_source.interactive = interactive;
        }

    public int getLineNumber()
    {
        return jj_input_stream.getBeginLine();
    }

        public void setConsoleReader(ConsoleReader c)
    {
        mConsoleReader = c;
        token_source.consoleReader = c;
    }

        abstract public void prompt();

        abstract protected void quit();

        abstract protected void printAliases() throws IOException;

        abstract protected void processFsCommand(String[] cmdTokens) throws IOException;

        abstract protected void processDescribe(String alias) throws IOException;

        abstract protected void processExplain(String alias, String script, boolean isVerbose, String format, String target, List<String> params, List<String> files) throws IOException, ParseException;

        abstract protected void processRegister(String jar) throws IOException;

        abstract protected void processSet(String key, String value) throws IOException, ParseException;

        abstract protected void processCat(String path) throws IOException;

        abstract protected void processCD(String path) throws IOException;

        abstract protected void processDump(String alias) throws IOException;

        abstract protected void processKill(String jobid) throws IOException;

        abstract protected void processLS(String path) throws IOException;

        abstract protected void processPWD() throws IOException;

        abstract protected void printHelp();

        abstract protected void processMove(String src, String dst) throws IOException;

        abstract protected void processCopy(String src, String dst) throws IOException;

        abstract protected void processCopyToLocal(String src, String dst) throws IOException;

        abstract protected void processCopyFromLocal(String src, String dst) throws IOException;

        abstract protected void processMkdir(String dir) throws IOException;

        abstract protected void processPig(String cmd) throws IOException;

        abstract protected void processRemove(String path, String opt) throws IOException;

        abstract protected void processIllustrate(String alias) throws IOException;

        abstract protected void processScript(String script, boolean batch, List<String> params, List<String> files) throws IOException, ParseException;

        static String unquote(String s)
        {
                if (s.charAt(0) == '\u005c'' && s.charAt(s.length()-1) == '\u005c'')
                        return s.substring(1, s.length()-1);
                else
                        return s;
        }

  final public void parse() throws ParseException, IOException {
        Token t1, t2;
        String val = null;
        List<String> cmdTokens = new ArrayList<String>();
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case EOL:
      jj_consume_token(EOL);
         prompt();
      break;
    case FS:
      jj_consume_token(FS);
      label_1:
      while (true) {
        t1 = GetPath();
                cmdTokens.add(t1.image);
                while(true){
                        try{
                                t1=GetPath();
                                cmdTokens.add(t1.image);
                        }catch(ParseException e){
                                break;
                        }
                }
                processFsCommand(cmdTokens.toArray(new String[cmdTokens.size()]));
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case CAT:
        case CD:
        case COPY:
        case COPYFROMLOCAL:
        case COPYTOLOCAL:
        case DUMP:
        case DESCRIBE:
        case ALIASES:
        case EXPLAIN:
        case HELP:
        case KILL:
        case LS:
        case MOVE:
        case MKDIR:
        case PWD:
        case QUIT:
        case REGISTER:
        case REMOVE:
        case SET:
        case RUN:
        case EXEC:
        case SCRIPT_DONE:
        case IDENTIFIER:
        case PATH:
          ;
          break;
        default:
          jj_la1[0] = jj_gen;
          break label_1;
        }
      }
      break;
    case CAT:
      jj_consume_token(CAT);
      label_2:
      while (true) {
        t1 = GetPath();
         processCat(t1.image);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case CAT:
        case CD:
        case COPY:
        case COPYFROMLOCAL:
        case COPYTOLOCAL:
        case DUMP:
        case DESCRIBE:
        case ALIASES:
        case EXPLAIN:
        case HELP:
        case KILL:
        case LS:
        case MOVE:
        case MKDIR:
        case PWD:
        case QUIT:
        case REGISTER:
        case REMOVE:
        case SET:
        case RUN:
        case EXEC:
        case SCRIPT_DONE:
        case IDENTIFIER:
        case PATH:
          ;
          break;
        default:
          jj_la1[1] = jj_gen;
          break label_2;
        }
      }
      break;
    case CD:
      jj_consume_token(CD);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CAT:
      case CD:
      case COPY:
      case COPYFROMLOCAL:
      case COPYTOLOCAL:
      case DUMP:
      case DESCRIBE:
      case ALIASES:
      case EXPLAIN:
      case HELP:
      case KILL:
      case LS:
      case MOVE:
      case MKDIR:
      case PWD:
      case QUIT:
      case REGISTER:
      case REMOVE:
      case SET:
      case RUN:
      case EXEC:
      case SCRIPT_DONE:
      case IDENTIFIER:
      case PATH:
        t1 = GetPath();
                 processCD(t1.image);
        break;
      default:
        jj_la1[2] = jj_gen;
                 processCD(null);
      }
      break;
    case COPY:
      jj_consume_token(COPY);
      t1 = GetPath();
      t2 = GetPath();
         processCopy(t1.image, t2.image);
      break;
    case COPYFROMLOCAL:
      jj_consume_token(COPYFROMLOCAL);
      t1 = GetPath();
      t2 = GetPath();
         processCopyFromLocal(t1.image, t2.image);
      break;
    case COPYTOLOCAL:
      jj_consume_token(COPYTOLOCAL);
      t1 = GetPath();
      t2 = GetPath();
         processCopyToLocal(t1.image, t2.image);
      break;
    case DUMP:
      jj_consume_token(DUMP);
      t1 = jj_consume_token(IDENTIFIER);
         processDump(t1.image);
      break;
    case ILLUSTRATE:
      jj_consume_token(ILLUSTRATE);
      t1 = jj_consume_token(IDENTIFIER);
         processIllustrate(t1.image);
      break;
    case DESCRIBE:
      jj_consume_token(DESCRIBE);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case IDENTIFIER:
        t1 = jj_consume_token(IDENTIFIER);
         processDescribe(t1.image);
        break;
      default:
        jj_la1[3] = jj_gen;
                 processDescribe(null);
      }
      break;
    case ALIASES:
      jj_consume_token(ALIASES);
         printAliases();
      break;
    case EXPLAIN:
      Explain();
      break;
    case HELP:
      jj_consume_token(HELP);
         printHelp();
      break;
    case KILL:
      jj_consume_token(KILL);
      t1 = jj_consume_token(IDENTIFIER);
         processKill(t1.image);
      break;
    case LS:
      jj_consume_token(LS);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case CAT:
      case CD:
      case COPY:
      case COPYFROMLOCAL:
      case COPYTOLOCAL:
      case DUMP:
      case DESCRIBE:
      case ALIASES:
      case EXPLAIN:
      case HELP:
      case KILL:
      case LS:
      case MOVE:
      case MKDIR:
      case PWD:
      case QUIT:
      case REGISTER:
      case REMOVE:
      case SET:
      case RUN:
      case EXEC:
      case SCRIPT_DONE:
      case IDENTIFIER:
      case PATH:
        t1 = GetPath();
                 processLS(t1.image);
        break;
      default:
        jj_la1[4] = jj_gen;
                 processLS(null);
      }
      break;
    case MOVE:
      jj_consume_token(MOVE);
      t1 = GetPath();
      t2 = GetPath();
         processMove(t1.image, t2.image);
      break;
    case MKDIR:
      jj_consume_token(MKDIR);
      t1 = GetPath();
         processMkdir(t1.image);
      break;
    case PIG:
      t1 = jj_consume_token(PIG);
         processPig(t1.image);
      break;
    case PWD:
      jj_consume_token(PWD);
         processPWD();
      break;
    case QUIT:
      jj_consume_token(QUIT);
         quit();
      break;
    case REGISTER:
      jj_consume_token(REGISTER);
      t1 = GetPath();
         processRegister(unquote(t1.image));
      break;
    case RUN:
    case EXEC:
      Script();
      break;
    case REMOVE:
      jj_consume_token(REMOVE);
      label_3:
      while (true) {
        t1 = GetPath();
                 processRemove(t1.image, null);
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case CAT:
        case CD:
        case COPY:
        case COPYFROMLOCAL:
        case COPYTOLOCAL:
        case DUMP:
        case DESCRIBE:
        case ALIASES:
        case EXPLAIN:
        case HELP:
        case KILL:
        case LS:
        case MOVE:
        case MKDIR:
        case PWD:
        case QUIT:
        case REGISTER:
        case REMOVE:
        case SET:
        case RUN:
        case EXEC:
        case SCRIPT_DONE:
        case IDENTIFIER:
        case PATH:
          ;
          break;
        default:
          jj_la1[5] = jj_gen;
          break label_3;
        }
      }
      break;
    case REMOVEFORCE:
      jj_consume_token(REMOVEFORCE);
      label_4:
      while (true) {
        t1 = GetPath();
                 processRemove(t1.image, "force");
        switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
        case CAT:
        case CD:
        case COPY:
        case COPYFROMLOCAL:
        case COPYTOLOCAL:
        case DUMP:
        case DESCRIBE:
        case ALIASES:
        case EXPLAIN:
        case HELP:
        case KILL:
        case LS:
        case MOVE:
        case MKDIR:
        case PWD:
        case QUIT:
        case REGISTER:
        case REMOVE:
        case SET:
        case RUN:
        case EXEC:
        case SCRIPT_DONE:
        case IDENTIFIER:
        case PATH:
          ;
          break;
        default:
          jj_la1[6] = jj_gen;
          break label_4;
        }
      }
      break;
    case SCRIPT_DONE:
      jj_consume_token(SCRIPT_DONE);
         quit();
      break;
    case SET:
      jj_consume_token(SET);
      t1 = GetKey();
      t2 = GetValue();
                 processSet(t1.image, unquote(t2.image));
      break;
    case 0:
      jj_consume_token(0);
         quit();
      break;
    case SEMICOLON:
      jj_consume_token(SEMICOLON);

      break;
    default:
      jj_la1[7] = jj_gen;
      handle_invalid_command(EOL);
         prompt();
    }
  }

  final public void Explain() throws ParseException, IOException {
        Token t;
        String alias = null;
        String script = null;
        String format="text";
        String target=null;
        boolean isVerbose = true;
        ArrayList<String> params;
        ArrayList<String> files;
    jj_consume_token(EXPLAIN);
                params = new ArrayList<String>();
                files = new ArrayList<String>();
    label_5:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PARAM:
      case PARAM_FILE:
      case SCRIPT:
      case DOT:
      case OUT:
      case BRIEF:
        ;
        break;
      default:
        jj_la1[8] = jj_gen;
        break label_5;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case BRIEF:
        jj_consume_token(BRIEF);
                 isVerbose = false;
        break;
      case DOT:
        jj_consume_token(DOT);
                 format = "dot";
        break;
      case OUT:
        jj_consume_token(OUT);
        t = GetPath();
                 target = t.image;
        break;
      case SCRIPT:
        jj_consume_token(SCRIPT);
        t = GetPath();
                 script = t.image;
        break;
      case PARAM:
        jj_consume_token(PARAM);
        t = GetPath();
                 params.add(t.image);
        break;
      case PARAM_FILE:
        jj_consume_token(PARAM_FILE);
        t = GetPath();
                 files.add(t.image);
        break;
      default:
        jj_la1[9] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IDENTIFIER:
      t = jj_consume_token(IDENTIFIER);
                 alias = t.image;
      break;
    default:
      jj_la1[10] = jj_gen;
      ;
    }
         processExplain(alias, script, isVerbose, format, target, params, files);
  }

  final public void Script() throws ParseException, IOException {
    Token t;
    String script = null;
    boolean batch = false;
    ArrayList<String> params;
    ArrayList<String> files;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case RUN:
      jj_consume_token(RUN);
                 batch = false;
      break;
    case EXEC:
      jj_consume_token(EXEC);
                 batch = true;
      break;
    default:
      jj_la1[11] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
                params = new ArrayList<String>();
                files = new ArrayList<String>();
    label_6:
    while (true) {
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PARAM:
      case PARAM_FILE:
        ;
        break;
      default:
        jj_la1[12] = jj_gen;
        break label_6;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PARAM:
        jj_consume_token(PARAM);
        t = GetPath();
                 params.add(t.image);
        break;
      case PARAM_FILE:
        jj_consume_token(PARAM_FILE);
        t = GetPath();
                 files.add(t.image);
        break;
      default:
        jj_la1[13] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
    }
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case CAT:
    case CD:
    case COPY:
    case COPYFROMLOCAL:
    case COPYTOLOCAL:
    case DUMP:
    case DESCRIBE:
    case ALIASES:
    case EXPLAIN:
    case HELP:
    case KILL:
    case LS:
    case MOVE:
    case MKDIR:
    case PWD:
    case QUIT:
    case REGISTER:
    case REMOVE:
    case SET:
    case RUN:
    case EXEC:
    case SCRIPT_DONE:
    case IDENTIFIER:
    case PATH:
      t = GetPath();
                 script = t.image;
      break;
    default:
      jj_la1[14] = jj_gen;
      ;
    }
         processScript(script, batch, params, files);
  }

  final public Token GetPath() throws ParseException {
        Token t;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case IDENTIFIER:
      t = jj_consume_token(IDENTIFIER);
      break;
    case PATH:
      t = jj_consume_token(PATH);
      break;
    case CAT:
    case CD:
    case COPY:
    case COPYFROMLOCAL:
    case COPYTOLOCAL:
    case DUMP:
    case DESCRIBE:
    case ALIASES:
    case EXPLAIN:
    case HELP:
    case KILL:
    case LS:
    case MOVE:
    case MKDIR:
    case PWD:
    case QUIT:
    case REGISTER:
    case REMOVE:
    case SET:
    case RUN:
    case EXEC:
    case SCRIPT_DONE:
      t = GetReserved();
      break;
    default:
      jj_la1[15] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
         {if (true) return t;}
    throw new Error("Missing return statement in function");
  }

  final public Token GetKey() throws ParseException {
        Token t;
    t = GetPath();
         {if (true) return t;}
    throw new Error("Missing return statement in function");
  }

  final public Token GetValue() throws ParseException {
        Token t;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case CAT:
    case CD:
    case COPY:
    case COPYFROMLOCAL:
    case COPYTOLOCAL:
    case DUMP:
    case DESCRIBE:
    case ALIASES:
    case EXPLAIN:
    case HELP:
    case KILL:
    case LS:
    case MOVE:
    case MKDIR:
    case PWD:
    case QUIT:
    case REGISTER:
    case REMOVE:
    case SET:
    case RUN:
    case EXEC:
    case SCRIPT_DONE:
    case IDENTIFIER:
    case PATH:
      t = GetPath();
      break;
    case QUOTEDSTRING:
      t = jj_consume_token(QUOTEDSTRING);
      break;
    default:
      jj_la1[16] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
         {if (true) return t;}
    throw new Error("Missing return statement in function");
  }

  final public Token GetReserved() throws ParseException {
        Token t;
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case CAT:
      t = jj_consume_token(CAT);
      break;
    case CD:
      t = jj_consume_token(CD);
      break;
    case COPY:
      t = jj_consume_token(COPY);
      break;
    case COPYFROMLOCAL:
      t = jj_consume_token(COPYFROMLOCAL);
      break;
    case COPYTOLOCAL:
      t = jj_consume_token(COPYTOLOCAL);
      break;
    case DUMP:
      t = jj_consume_token(DUMP);
      break;
    case DESCRIBE:
      t = jj_consume_token(DESCRIBE);
      break;
    case ALIASES:
      t = jj_consume_token(ALIASES);
      break;
    case EXPLAIN:
      t = jj_consume_token(EXPLAIN);
      break;
    case HELP:
      t = jj_consume_token(HELP);
      break;
    case KILL:
      t = jj_consume_token(KILL);
      break;
    case LS:
      t = jj_consume_token(LS);
      break;
    case MOVE:
      t = jj_consume_token(MOVE);
      break;
    case MKDIR:
      t = jj_consume_token(MKDIR);
      break;
    case PWD:
      t = jj_consume_token(PWD);
      break;
    case QUIT:
      t = jj_consume_token(QUIT);
      break;
    case REGISTER:
      t = jj_consume_token(REGISTER);
      break;
    case REMOVE:
      t = jj_consume_token(REMOVE);
      break;
    case SET:
      t = jj_consume_token(SET);
      break;
    case SCRIPT_DONE:
      t = jj_consume_token(SCRIPT_DONE);
      break;
    case RUN:
      t = jj_consume_token(RUN);
      break;
    case EXEC:
      t = jj_consume_token(EXEC);
      break;
    default:
      jj_la1[17] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
         {if (true) return t;}
    throw new Error("Missing return statement in function");
  }

  void handle_invalid_command(int kind) throws ParseException {
        throw generateParseException();
  }

  /** Generated Token Manager. */
  public PigScriptParserTokenManager token_source;
  JavaCharStream jj_input_stream;
  /** Current token. */
  public Token token;
  /** Next token. */
  public Token jj_nt;
  private int jj_ntk;
  private int jj_gen;
  final private int[] jj_la1 = new int[18];
  static private int[] jj_la1_0;
  static private int[] jj_la1_1;
  static private int[] jj_la1_2;
  static private int[] jj_la1_3;
  static {
      jj_la1_init_0();
      jj_la1_init_1();
      jj_la1_init_2();
      jj_la1_init_3();
   }
   private static void jj_la1_init_0() {
      jj_la1_0 = new int[] {0x35ffff40,0x35ffff40,0x35ffff40,0x0,0x35ffff40,0x35ffff40,0x35ffff40,0x3fffffc1,0xc0000000,0xc0000000,0x0,0x30000000,0xc0000000,0xc0000000,0x35ffff40,0x35ffff40,0x35ffff40,0x35ffff40,};
   }
   private static void jj_la1_init_1() {
      jj_la1_1 = new int[] {0x10,0x10,0x10,0x0,0x10,0x10,0x10,0x10,0xf,0xf,0x0,0x0,0x0,0x0,0x10,0x10,0x10,0x10,};
   }
   private static void jj_la1_init_2() {
      jj_la1_2 = new int[] {0x0,0x0,0x0,0x0,0x0,0x0,0x0,0xb0000000,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,0x0,};
   }
   private static void jj_la1_init_3() {
      jj_la1_3 = new int[] {0x180,0x180,0x180,0x80,0x180,0x180,0x180,0x0,0x0,0x0,0x80,0x0,0x0,0x0,0x180,0x180,0x380,0x0,};
   }

  /** Constructor with InputStream. */
  public PigScriptParser(java.io.InputStream stream) {
     this(stream, null);
  }
  /** Constructor with InputStream and supplied encoding */
  public PigScriptParser(java.io.InputStream stream, String encoding) {
    try { jj_input_stream = new JavaCharStream(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source = new PigScriptParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 18; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  public void ReInit(java.io.InputStream stream) {
     ReInit(stream, null);
  }
  /** Reinitialise. */
  public void ReInit(java.io.InputStream stream, String encoding) {
    try { jj_input_stream.ReInit(stream, encoding, 1, 1); } catch(java.io.UnsupportedEncodingException e) { throw new RuntimeException(e); }
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 18; i++) jj_la1[i] = -1;
  }

  /** Constructor. */
  public PigScriptParser(java.io.Reader stream) {
    jj_input_stream = new JavaCharStream(stream, 1, 1);
    token_source = new PigScriptParserTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 18; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 18; i++) jj_la1[i] = -1;
  }

  /** Constructor with generated Token Manager. */
  public PigScriptParser(PigScriptParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 18; i++) jj_la1[i] = -1;
  }

  /** Reinitialise. */
  public void ReInit(PigScriptParserTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 18; i++) jj_la1[i] = -1;
  }

  private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }


/** Get the next Token. */
  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

/** Get the specific Token. */
  final public Token getToken(int index) {
    Token t = token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.List jj_expentries = new java.util.ArrayList();
  private int[] jj_expentry;
  private int jj_kind = -1;

  /** Generate ParseException. */
  public ParseException generateParseException() {
    jj_expentries.clear();
    boolean[] la1tokens = new boolean[106];
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 18; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
          if ((jj_la1_1[i] & (1<<j)) != 0) {
            la1tokens[32+j] = true;
          }
          if ((jj_la1_2[i] & (1<<j)) != 0) {
            la1tokens[64+j] = true;
          }
          if ((jj_la1_3[i] & (1<<j)) != 0) {
            la1tokens[96+j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 106; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.add(jj_expentry);
      }
    }
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = (int[])jj_expentries.get(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  /** Enable tracing. */
  final public void enable_tracing() {
  }

  /** Disable tracing. */
  final public void disable_tracing() {
  }

}
