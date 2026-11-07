INSERT INTO genres (code, name, kopis_code, sort_order, active) VALUES
                                                                    ('theater',             '연극',        'AAAA', 1, true),
                                                                    ('dance',               '무용',        'BBBC', 2, true),
                                                                    ('popular_dance',       '대중무용',    'BBBE', 3, true),
                                                                    ('classical',           '클래식',      'CCCA', 4, true),
                                                                    ('korean_traditional',  '국악',        'CCCC', 5, true),
                                                                    ('concert',             '콘서트',      'CCCD', 6, true),
                                                                    ('complex',             '복합',        'EEEA', 7, true),
                                                                    ('circus_magic',        '서커스/마술', 'EEEB', 8, true),
                                                                    ('musical',             '뮤지컬',      'GGGA', 9, true)
    ON CONFLICT (code) DO UPDATE SET
    name       = EXCLUDED.name,
                              kopis_code = EXCLUDED.kopis_code,
                              sort_order = EXCLUDED.sort_order,
                              active     = EXCLUDED.active;