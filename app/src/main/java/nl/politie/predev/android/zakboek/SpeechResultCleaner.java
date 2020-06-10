package nl.politie.predev.android.zakboek;

public class SpeechResultCleaner {

	public static String cleanResult(String text){

		if(text==null){
			return "";
		}
		//Unk kan overal in de tekst voorkomen. Haal dit er uit.
		text = text.replace("<unk>", "").trim();

		//Er worden automatisch spaties en punten geplot. Dit klopt niet altijd, zeker als er <unk>s in het resultaat zitten.
		//Sloop de "verkeerde" spaties er uit
		while (text.contains(" .")) {
			text = text.replace(" .", ".");
		}
		while (text.contains("  ")) {
			text = text.replace("  ", " ");
		}
		//Zorg er wel voor dat we niet de hele string kwijt zijn. Dan hoeft ie niets te doen.
		//Het kan voorkomen dat na trimmen en alle <unk>'s er uit halen, er alleen een punt overblijft. Dat willen we ook niet, dus <= 1
		if (text.length() <= 1) {
			return "";
		}
		return text;

	}

}
