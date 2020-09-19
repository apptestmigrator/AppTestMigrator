package app.test.migrator.matching.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class LemmatizationAndPOSTagger {

    private StanfordCoreNLP pipeline;

    public LemmatizationAndPOSTagger(){
        Properties propsTokenize = new Properties();
        propsTokenize.put("annotators", "tokenize, ssplit, pos, lemma");
        pipeline = new StanfordCoreNLP(propsTokenize, false);
    }

    public List<String> getLemmatizedWord(String text) throws IOException {
        Annotation document = pipeline.process(text);

        List<String> lemmatizedWords = new ArrayList<String>();
        for(CoreMap sentence: document.get(SentencesAnnotation.class)){
            for(CoreLabel token: sentence.get(TokensAnnotation.class)){
                String word = token.get(TextAnnotation.class);
                String lemma = token.get(LemmaAnnotation.class);

                if (!word.equals("done") && !word.equals("ascending") && !word.equals("descending"))   lemmatizedWords.add(lemma);
                else lemmatizedWords.add(word);
            }
        }

        return lemmatizedWords;
    }

}