-- 홈 화면 배너용 이미지 주소. 포스터(세로 3:4)와 비율이 달라 별도 컬럼으로 둔다.
-- 값이 있는 공연만 배너에 노출하므로, 비어 있어도 무방하다.
ALTER TABLE performances ADD COLUMN banner_url TEXT;
