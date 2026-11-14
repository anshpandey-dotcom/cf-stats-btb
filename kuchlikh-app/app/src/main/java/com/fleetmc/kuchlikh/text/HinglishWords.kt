package com.fleetmc.kuchlikh.text

object HinglishWords {

    private fun parse(data: String): Map<String, String> {
        val map = HashMap<String, String>()
        data.split(Regex("\\s+")).forEach { entry ->
            val cut = entry.indexOf('=')
            if (cut > 0) map[entry.substring(0, cut)] = entry.substring(cut + 1)
        }
        return map
    }

    val common: Map<String, String> = parse(
        """
        hai=है hain=हैं h=है hu=हूँ hun=हूँ hoon=हूँ ho=हो tha=था thi=थी the=थे thin=थीं hoga=होगा hogi=होगी honge=होंगे
        hua=हुआ hui=हुई hue=हुए huye=हुए hona=होना hota=होता hoti=होती hote=होते
        main=मैं mai=मैं mein=में me=में mujhe=मुझे mujhko=मुझको mujhse=मुझसे mera=मेरा meri=मेरी mere=मेरे maine=मैंने
        hum=हम ham=हम humein=हमें hame=हमें hamein=हमें hume=हमें hamara=हमारा hamari=हमारी hamare=हमारे
        humara=हमारा humari=हमारी humare=हमारे humne=हमने hamne=हमने humse=हमसे
        tu=तू tum=तुम tumhe=तुम्हें tumhein=तुम्हें tumko=तुमको tumse=तुमसे tumhara=तुम्हारा tumhari=तुम्हारी
        tumhare=तुम्हारे tumne=तुमने tera=तेरा teri=तेरी tere=तेरे tujhe=तुझे tujhse=तुझसे
        aap=आप ap=आप aapka=आपका aapki=आपकी aapke=आपके aapko=आपको aapne=आपने aapse=आपसे apna=अपना apni=अपनी apne=अपने
        woh=वह wo=वो vo=वो voh=वह yeh=यह ye=ये yah=यह vah=वह ve=वे
        is=इस iska=इसका iski=इसकी iske=इसके isko=इसको isse=इससे isme=इसमें ismein=इसमें isne=इसने ise=इसे
        us=उस uska=उसका uski=उसकी uske=उसके usko=उसको usse=उससे usme=उसमें usmein=उसमें usne=उसने use=उसे
        in=इन inka=इनका inki=इनकी inke=इनके inko=इनको inhe=इन्हें inhein=इन्हें inhone=इन्होंने inse=इनसे
        un=उन unka=उनका unki=उनकी unke=उनके unko=उनको unhe=उन्हें unhein=उन्हें unhone=उन्होंने unse=उनसे
        jo=जो jis=जिस jiska=जिसका jiski=जिसकी jiske=जिसके jisko=जिसको jisne=जिसने jise=जिसे jab=जब jaha=जहाँ jahan=जहाँ
        jaise=जैसे jaisa=जैसा jaisi=जैसी jitna=जितना jitni=जितनी jitne=जितने
        kis=किस kiska=किसका kiski=किसकी kiske=किसके kisko=किसको kisne=किसने kise=किसे kisi=किसी
        kya=क्या kyu=क्यों kyun=क्यों kyon=क्यों kyunki=क्योंकि kyonki=क्योंकि kyuki=क्योंकि kaun=कौन kon=कौन kab=कब
        kaha=कहाँ kahan=कहाँ kaise=कैसे kaisa=कैसा kaisi=कैसी kitna=कितना kitni=कितनी kitne=कितने
        kuch=कुछ kuchh=कुछ koi=कोई kai=कई kabhi=कभी kahin=कहीं kahi=कहीं
        ka=का ki=की ke=के ko=को se=से ne=ने par=पर pe=पे tak=तक liye=लिए lie=लिए saath=साथ sath=साथ bina=बिना
        baad=बाद pehle=पहले pahle=पहले phir=फिर fir=फिर
        aur=और or=और ya=या lekin=लेकिन magar=मगर isliye=इसलिए isiliye=इसीलिए toh=तो to=तो bhi=भी hi=ही na=ना
        nahi=नहीं nahin=नहीं nhi=नहीं mat=मत haan=हाँ han=हाँ ji=जी
        ab=अब abhi=अभी tab=तब tabhi=तभी yaha=यहाँ yahan=यहाँ waha=वहाँ wahan=वहाँ vaha=वहाँ vahan=वहाँ
        idhar=इधर udhar=उधर aaj=आज kal=कल parso=परसों roz=रोज़ hamesha=हमेशा
        bahut=बहुत bohot=बहुत bahot=बहुत boht=बहुत zyada=ज़्यादा jyada=ज़्यादा kam=कम thoda=थोड़ा thodi=थोड़ी thode=थोड़े
        sab=सब sabhi=सभी sara=सारा sari=सारी saare=सारे sare=सारे pura=पूरा poora=पूरा puri=पूरी poori=पूरी
        accha=अच्छा acha=अच्छा achha=अच्छा achchha=अच्छा acchi=अच्छी achi=अच्छी achhi=अच्छी acche=अच्छे ache=अच्छे achhe=अच्छे
        bura=बुरा buri=बुरी bure=बुरे bada=बड़ा badi=बड़ी bade=बड़े chota=छोटा chhota=छोटा choti=छोटी chhoti=छोटी
        chote=छोटे chhote=छोटे naya=नया nayi=नई naye=नए purana=पुराना purani=पुरानी purane=पुराने
        ek=एक do=दो teen=तीन tin=तीन char=चार chaar=चार paanch=पाँच panch=पाँच chhe=छह saat=सात aath=आठ nau=नौ das=दस
        sau=सौ hazar=हज़ार hazaar=हज़ार lakh=लाख pehla=पहला pahla=पहला pehli=पहली dusra=दूसरा doosra=दूसरा
        dusri=दूसरी doosri=दूसरी teesra=तीसरा
        kiya=किया kiye=किए kar=कर karna=करना karta=करता karti=करती karte=करते karo=करो kare=करे karen=करें
        karenge=करेंगे karunga=करूँगा karungi=करूँगी karega=करेगा karegi=करेगी karke=करके
        gaya=गया gayi=गई gai=गई gaye=गए gye=गए gya=गया aaya=आया aayi=आई aaye=आए aya=आया
        diya=दिया diye=दिए di=दी liya=लिया li=ली
        raha=रहा rahi=रही rahe=रहे rha=रहा rhi=रही rhe=रहे rehna=रहना rahna=रहना rehta=रहता rehti=रहती rehte=रहते
        sakta=सकता sakti=सकती sakte=सकते chahiye=चाहिए chahie=चाहिए chahta=चाहता chahti=चाहती chahte=चाहते
        jana=जाना jata=जाता jati=जाती jate=जाते jao=जाओ jaa=जा aana=आना ana=आना aata=आता aati=आती aate=आते
        aao=आओ aaiye=आइए
        khana=खाना pani=पानी paani=पानी ghar=घर kaam=काम naam=नाम nam=नाम baat=बात bat=बात baatein=बातें din=दिन
        raat=रात rat=रात subah=सुबह shaam=शाम sham=शाम dopahar=दोपहर samay=समय waqt=वक़्त wakt=वक़्त
        log=लोग logo=लोगों logon=लोगों aadmi=आदमी admi=आदमी aurat=औरत ladka=लड़का ladki=लड़की ladke=लड़के
        ladkiyan=लड़कियाँ bachcha=बच्चा baccha=बच्चा bachche=बच्चे bacche=बच्चे dost=दोस्त bhai=भाई behen=बहन
        bahan=बहन maa=माँ papa=पापा pita=पिता mata=माता beta=बेटा beti=बेटी parivar=परिवार
        pyar=प्यार pyaar=प्यार dil=दिल zindagi=ज़िंदगी jindagi=ज़िंदगी duniya=दुनिया dunia=दुनिया desh=देश
        bharat=भारत hindi=हिंदी kitab=किताब kitaab=किताब kahani=कहानी kavita=कविता shabd=शब्द
        likhna=लिखना padhna=पढ़ना padhai=पढ़ाई school=स्कूल
        khush=ख़ुश khushi=ख़ुशी dukh=दुख dukhi=दुखी sach=सच jhooth=झूठ jhuth=झूठ sahi=सही galat=ग़लत galti=ग़लती
        zaroor=ज़रूर jarur=ज़रूर zaruri=ज़रूरी jaruri=ज़रूरी shayad=शायद bilkul=बिल्कुल
        theek=ठीक thik=ठीक thanda=ठंडा garam=गरम mushkil=मुश्किल aasan=आसान asan=आसान sundar=सुंदर
        namaste=नमस्ते namaskar=नमस्कार dhanyavad=धन्यवाद dhanyawad=धन्यवाद shukriya=शुक्रिया kripya=कृपया maaf=माफ़
        paisa=पैसा paise=पैसे rupaye=रुपये rupay=रुपये
        wala=वाला wali=वाली wale=वाले vala=वाला vali=वाली vale=वाले
        bhagwan=भगवान ishwar=ईश्वर matlab=मतलब samajh=समझ pata=पता yaad=याद yad=याद intezar=इंतज़ार
        sirf=सिर्फ़ bas=बस wapas=वापस vapas=वापस dobara=दोबारा
        upar=ऊपर neeche=नीचे niche=नीचे andar=अंदर bahar=बाहर paas=पास pas=पास door=दूर dur=दूर aage=आगे
        peeche=पीछे piche=पीछे saamne=सामने samne=सामने beech=बीच
        ek=एक mann=मन man=मन tan=तन jeevan=जीवन jivan=जीवन sapna=सपना sapne=सपने aankh=आँख aankhen=आँखें
        chehra=चेहरा haath=हाथ hath=हाथ pair=पैर sir=सिर awaz=आवाज़ awaaz=आवाज़ rasta=रास्ता raasta=रास्ता
        safar=सफ़र manzil=मंज़िल umeed=उम्मीद ummeed=उम्मीद vishwas=विश्वास himmat=हिम्मत mehnat=मेहनत
        kahaniyan=कहानियाँ kitabein=किताबें adhyay=अध्याय lekhak=लेखक pathak=पाठक
        """
    )

    val roots: Map<String, String> = parse(
        """
        kar=कर ho=हो ja=जा jaa=जा aa=आ de=दे le=ले kha=खा khaa=खा pi=पी pee=पी so=सो ro=रो dekh=देख sun=सुन
        bol=बोल likh=लिख padh=पढ़ chal=चल baith=बैठ uth=उठ ruk=रुक mil=मिल bana=बना ban=बन samajh=समझ soch=सोच
        jaan=जान maan=मान rakh=रख khel=खेल has=हँस hans=हँस daal=डाल nikal=निकल pahunch=पहुँच bhej=भेज khol=खोल
        laa=ला la=ला bata=बता dikha=दिखा dikh=दिख sikha=सिखा seekh=सीख sikh=सीख puch=पूछ pooch=पूछ kah=कह keh=कह
        rah=रह reh=रह sak=सक lag=लग bach=बच bech=बेच kharid=ख़रीद khareed=ख़रीद dhundh=ढूँढ dhoondh=ढूँढ
        maar=मार mar=मर gir=गिर ud=उड़ tod=तोड़ jod=जोड़ chhod=छोड़ chod=छोड़ pakad=पकड़ daud=दौड़ bhaag=भाग
        naach=नाच nach=नाच gaa=गा ga=गा laga=लगा chala=चला jaga=जगा jaag=जाग kaat=काट dho=धो pehen=पहन pahan=पहन
        bhool=भूल bhul=भूल jeet=जीत jit=जीत haar=हार badal=बदल maang=माँग mang=माँग chah=चाह chaah=चाह
        ghoom=घूम ghum=घूम tair=तैर chadh=चढ़ utar=उतर pehchan=पहचान pahchan=पहचान samjha=समझा bula=बुला
        mila=मिला hata=हटा hat=हट bhar=भर dar=डर lad=लड़ jal=जल jala=जला pak=पक paka=पका
        baandh=बाँध bandh=बाँध daba=दबा ghus=घुस phenk=फेंक khinch=खींच kheench=खींच chun=चुन gin=गिन bun=बुन
        kama=कमा ghata=घटा badha=बढ़ा badh=बढ़ sula=सुला pila=पिला khila=खिला sambhal=सँभाल sambhaal=सँभाल
        dhak=ढक chhup=छुप chhupa=छुपा chupa=छुपा jee=जी ji=जी mana=मना chahiye=चाहिए nibha=निभा bita=बिता
        beet=बीत bit=बीत guzar=गुज़र guzaar=गुज़ार nikaal=निकाल utha=उठा bitha=बिठा laut=लौट lauta=लौटा
        """
    )
}
