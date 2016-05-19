package od.pages;

import net.serenitybdd.core.annotations.findby.FindBy;
import net.serenitybdd.core.pages.PageObject;
import net.serenitybdd.core.pages.WebElementFacade;

public class TokensPage extends PageObject {

    //region WEB ELEMENTS: Fields

    @FindBy(xpath = "*//div[@class=\"b-logs-view b-logs-view_token ng-binding\"]")
    public WebElementFacade token;

    @FindBy(xpath = "xhtml:html/xhtml:body/xhtml:pre")
    public WebElementFacade peerID;

    @FindBy(xpath = "*//*[contains(text(),\"BEGIN PGP PUBLIC KEY BLOCK\")]")
    public WebElementFacade remotePGPKey;

    @FindBy(xpath = "xhtml:html/xhtml:body/xhtml:pre")
    public WebElementFacade environmentData;

    @FindBy(xpath = "*//*[contains(text(),\"BEGIN PGP PUBLIC KEY BLOCK\")]")
    public WebElementFacade environmentPGPKey;

    //endregion

    //region WEB ELEMENTS: Buttons

    @FindBy(xpath = "*//button[@class=\"b-btn b-btn_green\"]")
    public WebElementFacade buttonAddToken;

    public String sikuliButtonShowToken = "src/test/resources/imgs/buttons/buttonShowToken.png";
    //endregion

    //region WEB ELEMENTS: Checkboxes

    //endregion

    //region WEB ELEMENTS: Links

    //endregion

    //region WEB ELEMENTS: Tables

    //endregion

    //region WEB ELEMENTS: Pickers

    //endregion

    //region WEB ELEMENTS: Selectors

    //endregion

    //region WEB ELEMENTS: Images

    //endregion

    //region WEB ELEMENTS: Icons

    //endregion

    //region WEB ELEMENTS: Headers

    @FindBy(xpath = "*//h1[contains(text(),\"Tokens\")]")
    public WebElementFacade headerTokens;

    //endregion
}