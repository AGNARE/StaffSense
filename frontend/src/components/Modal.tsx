import { useEffect, useId, useRef } from 'react';

/**
 * Традиционное модальное окно по центру экрана.
 *
 * <p>Заменяет боковые шторки: карточка дизайн-системы фиксированной ширины
 * (не шире 640px), ограниченная по высоте, с прокруткой только внутри тела.
 * Фон затемняется, закрытие — по Escape, клику по фону и кнопке в шапке.
 *
 * <p>Стили намеренно инлайновые и опираются на существующие классы
 * (`card`, `card-hd`, `btn-ghost`) — отдельный CSS-файл не нужен.
 */
export function Modal(props: {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  onClose: () => void;
  footer?: React.ReactNode;
  children: React.ReactNode;
  /** Ширина карточки; по умолчанию 640px. */
  maxWidth?: number;
}) {
  const { title, subtitle, onClose, footer, children, maxWidth = 640 } = props;
  const titleId = useId();
  const cardRef = useRef<HTMLDivElement>(null);
  // Обработчик Escape читается из ref: слушатель вешается один раз.
  const closeRef = useRef(onClose);
  closeRef.current = onClose;

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        closeRef.current();
      }
    };

    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    cardRef.current?.focus();

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, []);

  return (
    <div
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 120,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 24,
        background: 'rgba(6, 14, 26, .46)',
        backdropFilter: 'blur(2px)'
      }}
    >
      <div
        ref={cardRef}
        className="card"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
        style={{
          width: '100%',
          maxWidth,
          maxHeight: '86vh',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          outline: 'none',
          boxShadow: '0 24px 80px rgba(6, 14, 26, .32)'
        }}
      >
        <div className="card-hd" style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flex: 'none' }}>
          <div style={{ minWidth: 0 }}>
            <h2 className="t-h2" id={titleId}>{title}</h2>
            {subtitle && <div className="t-sm muted mt-4">{subtitle}</div>}
          </div>
          <div className="spacer" />
          <button
            type="button"
            className="btn btn-sm btn-ghost btn-icon"
            onClick={onClose}
            aria-label="Закрыть"
            title="Закрыть"
          >
            ✕
          </button>
        </div>

        <div style={{ padding: '18px 20px', overflowY: 'auto', flex: 1 }}>
          {children}
        </div>

        {footer && (
          <div
            style={{
              padding: '12px 20px',
              borderTop: '1px solid var(--line)',
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              background: 'var(--surface-2)',
              flex: 'none'
            }}
          >
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}
