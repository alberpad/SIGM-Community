-- REGISTRO --
update scr_contador set contador = contador+1 where tablaid = 'SCR_CA';
insert into scr_ca values ((select contador from scr_contador where tablaid = 'SCR_CA'),'SUBS_JUST','SUBSANACION JUSTIFICACI�N',1,0,1,0, NOW(),'','1','0');
