INSERT INTO genres (code, name, kopis_code, sort_order, active) VALUES
                                                                    ('THEATER',            '연극',        'AAAA', 1, true),
                                                                    ('DANCE',              '무용',        'BBBC', 2, true),
                                                                    ('POPULAR_DANCE',      '대중무용',    'BBBE', 3, true),
                                                                    ('CLASSICAL',          '클래식',      'CCCA', 4, true),
                                                                    ('KOREAN_TRADITIONAL', '국악',        'CCCC', 5, true),
                                                                    ('POPULAR_MUSIC',      '대중음악',    'CCCD', 6, true),
                                                                    ('COMPLEX',            '복합',        'EEEA', 7, true),
                                                                    ('CIRCUS_MAGIC',       '서커스/마술', 'EEEB', 8, true),
                                                                    ('MUSICAL',            '뮤지컬',      'GGGA', 9, true)
    ON CONFLICT (code) DO NOTHING;