#include <SDL3/SDL.h>
#include <SDL3/SDL_opengl.h>

#include "game.h"

PFNGLACTIVETEXTUREARBPROC			procptr_glActiveTextureARB			= NULL;
PFNGLCLIENTACTIVETEXTUREARBPROC		procptr_glClientActiveTextureARB	= NULL;

#if __ANDROID__
void glActiveTexture(GLenum texture);
void glClientActiveTexture(GLenum texture);
#endif

void OGL_InitFunctions(void)
{
#if __ANDROID__
	// gl4es exports the multitexture functions directly (GLES has no *ARB names).
	procptr_glActiveTextureARB			= (PFNGLACTIVETEXTUREARBPROC) glActiveTexture;
	procptr_glClientActiveTextureARB	= (PFNGLCLIENTACTIVETEXTUREARBPROC) glClientActiveTexture;
	GAME_ASSERT(procptr_glActiveTextureARB);
	GAME_ASSERT(procptr_glClientActiveTextureARB);
	return;
#endif
	procptr_glActiveTextureARB			= (PFNGLACTIVETEXTUREARBPROC) SDL_GL_GetProcAddress("glActiveTextureARB");
	procptr_glClientActiveTextureARB	= (PFNGLCLIENTACTIVETEXTUREARBPROC) SDL_GL_GetProcAddress("glClientActiveTextureARB");

	GAME_ASSERT(procptr_glActiveTextureARB);
	GAME_ASSERT(procptr_glClientActiveTextureARB);
}
