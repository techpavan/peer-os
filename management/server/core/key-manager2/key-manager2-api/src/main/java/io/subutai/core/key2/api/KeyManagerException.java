package io.subutai.core.key2.api;


/**
 * Exception thrown by KeyManager methods
 */
public class KeyManagerException extends Exception
{
    public KeyManagerException(final Throwable cause)
    {
        super( cause );
    }


    public KeyManagerException(final String message)
    {
        super( message );
    }
}
