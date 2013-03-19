package edu.berkeley.nlp.PCFGLA;

/**
 * 
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class WriteGrammarToTextFile {

    /**
     * @param args
     */
    public static void main(final String[] args) {

        if (args.length < 2) {
            System.out
                    .println("usage: java -cp berkeleyParser.jar edu/berkeley/nlp/parser/WriteGrammarToTextFile <grammar> <output file name> [<threshold>] \n "
                            + "reads in a serialized grammar file and writes it to a text file.");
            System.exit(2);
        }

        final String inFileName = args[0];
        final String outName = args[1];

        System.out.println("Loading grammar from file " + inFileName + ".");
        final ParserData pData = ParserData.Load(inFileName);
        if (pData == null) {
            System.out.println("Failed to load grammar from file" + inFileName + ".");
            System.exit(1);
        }

        @SuppressWarnings("null")
        final Grammar grammar = pData.getGrammar();

        final Lexicon lexicon = pData.getLexicon();
        Numberer.setNumberers(pData.getNumbs());
        // final Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
        grammar.splitRules();

        if (args.length > 2) {
            final double filter = Double.parseDouble(args[2]);
            grammar.removeUnlikelyRules(filter, 1.0);
            lexicon.removeUnlikelyTags(filter, 1.0);
        }

        System.out.println("Writing output to files " + outName + ".xxx");
        Writer output = null;
        try {
            output = new BufferedWriter(new FileWriter(outName + ".grammar"));
            // output.write(grammar.toString());
            grammar.writeData(output);
            output.close();

            output = new BufferedWriter(new FileWriter(outName + ".splits"));
            // output.write(grammar.toString());
            grammar.writeSplitTrees(output);
            output.close();

            output = new BufferedWriter(new FileWriter(outName + ".lexicon"));
            output.write(lexicon.toString());
            output.close();

            output = new BufferedWriter(new FileWriter(outName + ".words"));
            for (final String word : lexicon.wordCounter.keySet()) {
                output.write(word + "\n");
            }
            output.close();

        } catch (final IOException ex) {
            ex.printStackTrace();
        }

    }

}