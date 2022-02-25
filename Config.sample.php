<?php

declare(strict_types=1);

class Config
{
    public static string $sqliteDBLocation = __DIR__ . '/osuDB.sqlite3';

    public static string $osuApiKey = '';
    public static int $osuUserId = 1234567890;

    public static string $twitterConsumerKey = '';
    public static string $twitterConsumerSecret = '';
    public static string $twitterAccessToken = '';
    public static string $twitterAccessTokenSecret = '';
}
