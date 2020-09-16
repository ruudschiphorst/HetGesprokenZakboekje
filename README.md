# HetGesprokenZakboekje

Dit project probeert een aantal doelen te behalen:
* Aantonen dat onze eigen apps kunnen communiceren met onze eigen S2T engine;
* Voorbeeldfunctionaliteit tonen voor een gesproken zakboekje (die het traditionele geschreven zakboekje moet vervangen)
* Voorbeeldcode om gebruik te maken van/ het implementeren van.
* Mensen enthousiats maken

Dit project heeft sterke samenhang met [de auth API](https://github.com/ruudschiphorst/stempol_auth_api) en [de db API](https://github.com/ruudschiphorst/stempol_db_api) die op de achtergrond worden gebruikt om resp. te authenticeren en om met de achterliggende database te communiceren, die de opgeslagen notities en bijlagen opslaat.

Belangrijke variabelen zoals connectionstrings en credentials worden opgeslagen in de SharedPreferences van de app, die alleen door deze app te lezen zijn. 

Het verbinden met de S2T kan via twee routes: gRPC en Websockets. De gRPC variant verbindt met de S2T workers volgens Google's standaarden en protocollen. Dit maakt het uitermate geschikt om te kunnen wisselen tussen onze eigen services en die van Google. Zie daartoe InsecureStempolRpcSpeechService (welke insecure is, omdat er geen gebruik gemaakt wordt van credentials e.d.). De websocket variant verbindt met de S2T service op www.stempol.nl. Zie daartoe WebSocketRecognitionService. Beide zijn gevat in de abstracte klasse AbstractSpeechService, zodat er in de code vrij gewisseld kan worden tussen beide zonder refactors. Er zit enkel een inherent verschil in hoe beide objecten worden opgebouwd en vereisen dus verschillende parameters.

De communicatie met de ab API is beveiligd met een zogenaamde JWT. In de achtergrond van de ap draait een thread die elke 10 minuten (het JWT is 15 minuten geldig) een nieuw token ophaalt o.b.v. de credentials die zijn opgeslagen in de SharedPreferences. 
Ook draait op de achtergrond een zogenaamde InternetStatusChecker die detecteert wanneer de verbinding met het internet is verbroken, bijvoorbeeld omdat iemand een tunnel in rijdt of iets dergelijks. Audio wordt standaard in een queue geplaatst voordat deze wordt verzonden. Is er geen internetverbinding beschikbaar, dan wordt de audio niet verzonden. Deze functionaliteit is niet vervolmaakt: er kan gedetecteerd worden of er een internetverbinding is en dat adressen kunnen worden geresolved. Er is echter te weinig feedback vanuit de server om vast te stellen of de spraak daadwerkelijk wordt ontvangen en verwerkt. 

Tot slot zijn er nog een aantal hulp classes, bijvoorbeeld om audio als FLAC te encoden, een aantal Listeners die een Callback faciliteren voor asynchrone resultaten en custom RecyclerViews. Deze wijzen zichzelf.