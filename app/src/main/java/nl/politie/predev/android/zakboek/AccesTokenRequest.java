package nl.politie.predev.android.zakboek;

public class AccesTokenRequest {

	public static AccesTokenRequest accesTokenRequest;

    private String accessToken;
    private String tokenType;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }




}
