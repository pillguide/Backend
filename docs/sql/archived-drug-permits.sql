-- Reviewed MFDS product pages. Apply with the same database role as reference-sync-state.sql.
-- Called inside the publisher's transaction after its drug and interaction writes have flushed.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

DO $$
DECLARE
    state reference_sync_state%ROWTYPE;
BEGIN
    SELECT * INTO STRICT state FROM reference_sync_state WHERE id = 1 FOR UPDATE;
    IF state.active_run IS NOT NULL THEN
        RAISE EXCEPTION 'Reference publication in progress. Retry after it completes.';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION apply_archived_drug_permits(batch_date date)
RETURNS integer LANGUAGE plpgsql AS $$
DECLARE
    changed integer;
BEGIN
    -- Keep the normal API records and timestamps intact. A live permit, even a partial one,
    -- takes precedence. No name-based matching, historical dosing or new DUR evidence.
    WITH archive(code, official_name, permit_status, ended_on, ingredients, effect, page_sha256) AS (
        VALUES
            ('199303108', '타이레놀정500밀리그람(아세트아미노펜)(수출명:TylenolExtraStrengthCaplets)', '취하', '2021-12-10', '아세트아미노펜', '1. 주효능·효과
감기로 인한 발열 및 동통(통증), 두통, 신경통, 근육통, 월경통, 염좌통(삔 통증)
2. 다음 질환에도 사용할 수 있다.
치통, 관절통, 류마티양 동통(통증)', '3763521af542a65d0f8f66c3c2707db4ea37a4c94493655a0c925709060cb375'),
            ('200200909', '화인탈츄어블정(수출명:OZIMETAL,CHEWABLETab)', '유효기간만료', '2020-07-01', '메클리진염산염수화물, 스코폴라민브롬화수소산염수화물', '멀미로 인한 현기증·구토·두통의 예방 및 완화', '0d4989d7a8e48accad2bb09382e3773c8d4445837c49c4fe739fd407c896a86a'),
            ('200202194', '리렉스펜정', '취하', '2023-07-24', '아세트아미노펜, 클로르족사존', '신경통, 근육통, 관절통, 요(허리)통, 염좌통(삔 통증), 견통(어깨결림), 근경련(근육경련), 근경직등 근육의 이상 긴장을 수반하는 제질환(여러 질환)', 'd968727efd152539e09c3824c929347455b948672e324f1ab773cba250ff6a66'),
            ('200308343', '이소켓서방정40밀리그램(이소소르비드이질산염)', '취하', '2023-05-31', '이소소르비드이질산염', '1. 협심증, 심근경색, 관경화증(만성허혈성심질환, 무증후성허혈성심질환, 동맥경화성심질환)
2. 울혈성심부전의 보조요법(강심배당체 또는 이뇨제와 병용)', 'c765fb4c68d2215e79119935997d4172e9411f40677272efdf644810c49f01ee'),
            ('200611524', '마도파정', '취하', '2023-01-06', '레보도파, 염산벤세라짓', '파킨슨증후군(약물로 인한 파킨슨병 제외)', '5196ab190e2fab3193a37080b1b0e89d9719793065d46e9ae8cb09406849c88c'),
            ('201006967', '울트라셋이알서방정', '폐업', '2022-03-21', '아세트아미노펜, 트라마돌염산염', '중등도-중증의 만성 통증', 'bb70a26b6b1aa8cf2fce273e2dbf5a0908da8de63835909f9b64807489002d97'),
            ('201800568', '바이오탑디캡슐', '유효기간만료', '2023-01-30', '엔테로코쿠스페칼리스T-110균, 바실루스메센테리쿠스토아균, 클로스트리디움부티리쿰토아균', '정장, 변비, 묽은 변, 복부팽만감, 장내이상발효', '9d9e24607ec35c0e9f76d3b04e6f0311d283df587278c527b9d88acce89e47e1'),
            ('201803000', '바이오탑하이캡슐', '유효기간만료', '2023-07-19', '엔테로코쿠스페칼리스T-110균, 바실루스메센테리쿠스토아균, 클로스트리디움부티리쿰토아균', '정장, 변비, 묽은 변, 복부팽만감, 장내이상발효', '4887cc4a81ace47af3702f3fee60ba4cadea76ab035f1dcb36248c5c9dbabd63')
    ), patched AS (
        UPDATE drug d
        SET authorization_status = 'ABNORMAL', info_status = 'PARTIAL',
            ingredient_display = a.ingredients, updated_at = timezone('Asia/Seoul', now())
        FROM archive a, drug_info i
        WHERE d.code = a.code AND i.drug_id = d.drug_id AND d.source_as_of = batch_date
          AND d.authorization_status = 'NO_OFFICIAL_RECORD' AND d.info_status = 'NO_OFFICIAL_RECORD'
          AND i.provenance_status = 'NO_OFFICIAL_RECORD' AND i.selected_sources_json IS NOT NULL
          AND i.effect IS NULL AND i.dosage_method IS NULL AND d.ingredient_display IS NULL
        RETURNING d.drug_id, a.*
    )
    UPDATE drug_info i
    SET effect = format('식약처 과거 허가 기록 (%s · %s)%s%s', p.permit_status, p.ended_on, chr(10), p.effect),
        provenance_status = 'PARTIAL', updated_at = timezone('Asia/Seoul', now()),
        selected_sources_json = (i.selected_sources_json::jsonb || jsonb_build_object(
            'archivedPermit', jsonb_build_object(
                'source', 'MFDS_NEDRUG', 'itemSeq', p.code, 'name', p.official_name,
                'authorization', p.permit_status, 'endedOn', p.ended_on, 'verifiedOn', '2026-11-22',
                'url', 'https://nedrug.mfds.go.kr/pbp/CCBBB01/getItemDetail?itemSeq=' || p.code,
                'pageSha256', p.page_sha256, 'ingredients', p.ingredients, 'effect', p.effect)))::text
    FROM patched p WHERE i.drug_id = p.drug_id;
    GET DIAGNOSTICS changed = ROW_COUNT;
    RETURN changed;
END $$;

CREATE OR REPLACE FUNCTION apply_archived_drug_permits_on_publish()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    PERFORM apply_archived_drug_permits(NEW.source_as_of);
    RETURN NULL;
END $$;

CREATE OR REPLACE TRIGGER archived_drug_permits_on_publish
AFTER UPDATE OF current_version ON reference_sync_state
FOR EACH ROW WHEN (NEW.current_version IS NOT NULL AND OLD.current_version IS DISTINCT FROM NEW.current_version)
EXECUTE FUNCTION apply_archived_drug_permits_on_publish();

-- Publish a distinct snapshot for existing readers without advancing the weekly sync clock.
-- Re-running on an already supplemented snapshot leaves its version unchanged.
DO $$
DECLARE
    state reference_sync_state%ROWTYPE;
    next_version text := gen_random_uuid()::text;
BEGIN
    SELECT * INTO STRICT state FROM reference_sync_state WHERE id = 1 FOR UPDATE;
    IF state.current_version IS NOT NULL AND apply_archived_drug_permits(state.source_as_of) > 0 THEN
        IF EXISTS (SELECT 1 FROM drug_interaction WHERE reference_version IS DISTINCT FROM state.current_version) THEN
            RAISE EXCEPTION 'Inconsistent reference generation';
        END IF;
        UPDATE drug_interaction SET reference_version = next_version WHERE reference_version = state.current_version;
        UPDATE reference_sync_state SET current_version = next_version WHERE id = 1;
    END IF;
END $$;
COMMIT;
