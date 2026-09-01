--
-- PostgreSQL database dump
--

\restrict fLfXEwVgKZf8vfNqkhupMOfKatXtnqDl7i4hqFG9BGe0e75r8mSHtEXcUDMWThU

-- Dumped from database version 18.3
-- Dumped by pg_dump version 18.3

-- Started on 2026-08-17 02:45:41

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 220 (class 1259 OID 43354)
-- Name: historial_cambios; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.historial_cambios (
    id uuid NOT NULL,
    persona_id uuid,
    nombre_anterior character varying(150),
    documento_anterior character varying(20),
    telefono_anterior character varying(20),
    creado_en timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);


ALTER TABLE public.historial_cambios OWNER TO postgres;

--
-- TOC entry 219 (class 1259 OID 34945)
-- Name: personas; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.personas (
    id uuid NOT NULL,
    nombre character varying(150) NOT NULL,
    documento character varying(20) NOT NULL,
    telefono character varying(20),
    version integer DEFAULT 1 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone
);


ALTER TABLE public.personas OWNER TO postgres;

--
-- TOC entry 5016 (class 0 OID 43354)
-- Dependencies: 220
-- Data for Name: historial_cambios; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.historial_cambios (id, persona_id, nombre_anterior, documento_anterior, telefono_anterior, creado_en) FROM stdin;
da0d4f5b-fe8f-4be0-8512-63288c8e0396	550e8400-e29b-41d4-a716-446655440000	Isabella Lopera Ayala	1118368430	3001234567	2026-08-16 22:10:52.91266-05
\.


--
-- TOC entry 5015 (class 0 OID 34945)
-- Dependencies: 219
-- Data for Name: personas; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.personas (id, nombre, documento, telefono, version, updated_at, deleted_at) FROM stdin;
f0e0fcd5-e587-470f-96ea-a6bccaa7c438	Isa Lopera	111746529	369874525	1	2026-07-20 22:03:08.539917-05	\N
df28eed3-44f5-42b0-a13e-7e819b2bfac7	isabellita	118369754	31547925	1	2026-07-20 22:03:08.828342-05	\N
a6c4974c-fe02-46c6-a926-bbba45b01248	Juan Lopera	176534589	368954254	1	2026-07-20 22:03:08.679868-05	\N
2fe4a40c-4d7e-4821-ab64-04547c3871e2	Silvana rosas	9876718735	3206547893	1	2026-07-20 22:26:25.137874-05	\N
1e4e212b-cfaa-4918-b77f-fc37cd2d387e	Nala Lopera	789654123	369872458	1	2026-07-20 22:26:25.493794-05	\N
796adb1f-96cb-4b1f-82db-544c53641fad	sebastian	556840244	3297288777	6	2026-07-27 13:01:36.615692-05	\N
03965493-8dc3-4fa4-bbc3-569c82f65d37	isa	112358488	3053121026	4	2026-07-27 13:01:37.107116-05	\N
116c8440-96df-4dfc-9208-4c45e48fb1bc	cristina	748596142	321654987	3	2026-07-27 13:31:42.952384-05	\N
1201e48a-849c-4a54-b959-8c9be64e2532	ingri	2364789	305316987	2	2026-07-27 13:31:43.007728-05	\N
be29aebc-c71b-410a-8504-38fc586ca717	Andres offline	9638457413	305368749	1	2026-07-27 13:47:22.824045-05	\N
4de3007c-adb5-4d4a-bec4-a81b286ef975	sahira	1118368439	123456789	1	2026-07-28 17:17:12.770044-05	\N
550e8400-e29b-41d4-a716-446655440000	Isabella	1118368430	3001234567	3	2026-08-16 22:10:52.840531-05	\N
34ba37ff-94f7-4af3-a96a-0c4ac478d025	Javier Cardona	522006849	3583695734	1	2026-08-16 23:28:03.233032-05	\N
83b2c804-6969-4183-a5e5-5a7ac03790e4	Ricarda 	123456	321654987	2	2026-08-17 01:54:02.141491-05	\N
fea57018-ab08-4ef9-969d-a2ff490b37c8	juan David	698547	321654987	1	2026-08-17 02:09:05.059064-05	\N
\.


--
-- TOC entry 4865 (class 2606 OID 43360)
-- Name: historial_cambios historial_cambios_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historial_cambios
    ADD CONSTRAINT historial_cambios_pkey PRIMARY KEY (id);


--
-- TOC entry 4863 (class 2606 OID 34956)
-- Name: personas personas_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.personas
    ADD CONSTRAINT personas_pkey PRIMARY KEY (id);


--
-- TOC entry 4866 (class 1259 OID 43366)
-- Name: idx_historial_persona_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_historial_persona_id ON public.historial_cambios USING btree (persona_id);


--
-- TOC entry 4867 (class 2606 OID 43361)
-- Name: historial_cambios historial_cambios_persona_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.historial_cambios
    ADD CONSTRAINT historial_cambios_persona_id_fkey FOREIGN KEY (persona_id) REFERENCES public.personas(id) ON DELETE CASCADE;


-- Completed on 2026-08-17 02:45:41

--
-- PostgreSQL database dump complete
--

\unrestrict fLfXEwVgKZf8vfNqkhupMOfKatXtnqDl7i4hqFG9BGe0e75r8mSHtEXcUDMWThU

