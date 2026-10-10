/****************************/
/*   	MISCSCREENS.C	    */
/* By Brian Greenstone      */
/* (c)2001 Pangea Software  */
/* (c)2023 Iliyas Jorio     */
/****************************/

#include "game.h"
#include "version.h"


static void DrawLegalImage(ObjNode* theNode)
{
	float s = 800;

	SDL_Log("OTTO: DrawLegalImage start");
	OGL_PushState();
	SDL_Log("OTTO: DrawLegalImage SetInfobarSpriteState");
	SetInfobarSpriteState(true);
	SDL_Log("OTTO: DrawLegalImage DrawInfobarSprite2");
	DrawInfobarSprite2(-s/2, -s/4, s, SPRITE_GROUP_LEVELSPECIFIC, 0);
	OGL_PopState();
	SDL_Log("OTTO: DrawLegalImage done");
}

void DoLegalScreen(void)
{
	SDL_Log("OTTO: DoLegalScreen starting");
	float timeout = 8.0f;

			/* SETUP VIEW */

	OGLSetupInputType viewDef;
	OGL_NewViewDef(&viewDef);

	viewDef.camera.hither 			= 10;
	viewDef.camera.yon 				= 3000;
	viewDef.view.clearColor 		= (OGLColorRGBA) {0,0,0,1};
	viewDef.styles.useFog			= false;

	SDL_Log("OTTO: DoLegalScreen - OGL_SetupWindow");
	OGL_SetupWindow(&viewDef);

			/* CREATE OBJECTS */

	SDL_Log("OTTO: DoLegalScreen - LoadSpriteGroup");
	LoadSpriteGroup(SPRITE_GROUP_LEVELSPECIFIC, 1, "legal");

	NewObjectDefinitionType imageDef =
	{
		.coord = {0,0,0},
		.genre = CUSTOM_GENRE,
		.type = 0,
		.scale = 1,
		.slot = SPRITE_SLOT,
	};
	SDL_Log("OTTO: DoLegalScreen - MakeNewObject imageDriver");
	ObjNode* imageDriver = MakeNewObject(&imageDef);
	imageDriver->CustomDrawFunction = DrawLegalImage;

	NewObjectDefinitionType versionDef =
	{
		.coord = {0,-80,0},
		.scale = 0.5f,
		.slot = SPRITE_SLOT + 1
	};
	SDL_Log("OTTO: DoLegalScreen - TextMesh_New");
	ObjNode* versionText = TextMesh_New("version " GAME_VERSION, kTextMeshAlignCenter, &versionDef);
	versionText->ColorFilter = (OGLColorRGBA) {0.3f, 0.5f, 0.2f, 1.0f};

		/***********/
		/* SHOW IT */
		/***********/

	SDL_Log("OTTO: DoLegalScreen - MakeFadeEvent");
	MakeFadeEvent(true, 1.0);
	SDL_Log("OTTO: DoLegalScreen - UpdateInput");
	UpdateInput();
	SDL_Log("OTTO: DoLegalScreen - CalcFramesPerSecond");
	CalcFramesPerSecond();

	SDL_Log("OTTO: DoLegalScreen - starting main loop");
	do
	{
		CalcFramesPerSecond();
		MoveObjects();
		OGL_DrawScene(DrawObjects);
		UpdateInput();

		timeout -= gFramesPerSecondFrac;
	} while (!UserWantsOut() && timeout > 0);

			/* FADE OUT */

	OGL_FadeOutScene(DrawObjects, NULL);

			/* CLEANUP */

	DeleteAllObjects();
	DisposeAllSpriteGroups();
	OGL_DisposeWindowSetup();
	Pomme_FlushPtrTracking(true);
}
